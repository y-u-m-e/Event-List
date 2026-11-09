package com.claneventlist;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.WorldType;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seasonal / event team roster with chat, friends, clan, and overhead badges.
 * Modeled on Bingo Team Icons, with stronger name parsing and duplicate detection.
 */
@Slf4j
@Singleton
public class EventTeamService
{
    static final int MAX_TEAMS = 10;

    private static final Set<ChatMessageType> PLAYER_CHAT_TYPES = EnumSet.of(
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.MODCHAT,
        ChatMessageType.AUTOTYPER,
        ChatMessageType.MODAUTOTYPER,
        ChatMessageType.FRIENDSCHAT,
        ChatMessageType.CLAN_CHAT,
        ChatMessageType.CLAN_GUEST_CHAT,
        ChatMessageType.CLAN_GIM_CHAT,
        ChatMessageType.PRIVATECHAT,
        ChatMessageType.PRIVATECHATOUT,
        ChatMessageType.MODPRIVATECHAT
    );

    private static final Set<ChatMessageType> BROADCAST_TYPES = EnumSet.of(
        ChatMessageType.CLAN_MESSAGE,
        ChatMessageType.CLAN_GUEST_MESSAGE,
        ChatMessageType.CLAN_GIM_MESSAGE
    );

    private static final Pattern BROADCAST_PATTERN = Pattern.compile(
        "^(.+?) (?:received (?:a drop|a new collection log item|special loot from a raid):|has a funny feeling|feels something weird sneaking)");

    private final Client client;
    private final ClientThread clientThread;
    private final ChatIconManager chatIconManager;
    private final ConfigManager configManager;
    private final EventListConfig config;

    // Static because ChatIconManager has no unregister: a plugin restart in the same JVM
    // must reuse the ids it already registered instead of leaking new icon slots.
    /** Chat / overhead icons (11×11). */
    private static int[] iconIds;
    /** Friends / clan list icons (14×12, Friend Notes size). */
    private static int[] friendsIconIds;

    /** Rebuilt off the client thread and swapped in whole; the client thread only reads it. */
    private volatile Map<String, Integer> playerTeams = Collections.emptyMap();
    /** Palette per team slot (index = team number), so overlays/chat don't parse config per frame. */
    private volatile EventTeamIconFactory.TeamPalette[] teamPalettes = new EventTeamIconFactory.TeamPalette[0];
    private final BufferedImage[] badgeImages = new BufferedImage[EventTeamIconFactory.PALETTE_SIZE];
    private volatile List<String> conflicts = Collections.emptyList();
    /**
     * friendsChatSetText and friendsChatSetPosition fire back-to-back for the same row;
     * this carries "we added an icon to this row" from the first to the second.
     */
    private Integer currentlyLayoutingTeam;
    private volatile boolean active;
    /**
     * Overhead badges are hidden in the Wilderness / on PvP worlds so they cannot work as
     * PvP clan-opponent identification (a Plugin Hub restriction). Updated once per tick.
     */
    private volatile boolean inPvpArea;

    @Inject
    EventTeamService(
        Client client,
        ClientThread clientThread,
        ChatIconManager chatIconManager,
        ConfigManager configManager,
        EventListConfig config
    )
    {
        this.client = client;
        this.clientThread = clientThread;
        this.chatIconManager = chatIconManager;
        this.configManager = configManager;
        this.config = config;
    }

    public void start()
    {
        active = true;
        ensureIconsRegistered();
        refreshSurfaces();
    }

    public void stop()
    {
        active = false;
        playerTeams = Collections.emptyMap();
        conflicts = Collections.emptyList();
        clientThread.invokeLater(this::retagChatHistory);
        rebuildFriendsList();
        rebuildClanLists();
    }

    /**
     * Re-apply roster tags after login or when the friends/clan side panels refresh.
     */
    public void refreshSurfaces()
    {
        rebuildPlayerTeams();
        rebuildFriendsList();
        rebuildClanLists();
        clientThread.invokeLater(this::retagChatHistory);
    }

    public void onGameTick()
    {
        inPvpArea = WorldType.isPvpWorld(client.getWorldType())
            || client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1;
    }

    public boolean isInPvpArea()
    {
        return inPvpArea;
    }

    public boolean hasRoster()
    {
        return !playerTeams.isEmpty();
    }

    public Integer teamFor(String standardizedName)
    {
        return playerTeams.get(standardizedName);
    }

    public BufferedImage badgeImage(int team)
    {
        EventTeamIconFactory.TeamPalette palette = cachedTeamPalette(team);
        return badgeImages[palette.ordinal()];
    }

    public List<String> getConflicts()
    {
        return conflicts;
    }

    public static String teamNamesKey(int team)
    {
        return "team" + team + "Names";
    }

    public static String teamColorKey(int team)
    {
        return "team" + team + "Color";
    }

    public static String teamLabelKey(int team)
    {
        return "team" + team + "Label";
    }

    public EventTeamIconFactory.TeamPalette teamPalette(int team)
    {
        String stored = configManager.getConfiguration(EventListConfig.CONFIG_GROUP, teamColorKey(team));
        if (stored != null && !stored.isEmpty())
        {
            EventTeamIconFactory.TeamPalette byKey = EventTeamIconFactory.TeamPalette.fromKey(stored);
            if (byKey != null)
            {
                return byKey;
            }
            if (stored.startsWith("#"))
            {
                try
                {
                    return EventTeamIconFactory.TeamPalette.nearest(Color.decode(stored));
                }
                catch (NumberFormatException ex)
                {
                    log.debug("Invalid stored color for team {}: {}", team, stored);
                }
            }
        }
        return EventTeamIconFactory.defaultPalette(team);
    }

    private EventTeamIconFactory.TeamPalette cachedTeamPalette(int team)
    {
        EventTeamIconFactory.TeamPalette[] cached = teamPalettes;
        return team > 0 && team < cached.length ? cached[team] : teamPalette(team);
    }

    private void rebuildTeamPalettes()
    {
        EventTeamIconFactory.TeamPalette[] next = new EventTeamIconFactory.TeamPalette[MAX_TEAMS + 1];
        for (int team = 1; team <= MAX_TEAMS; team++)
        {
            next[team] = teamPalette(team);
        }
        teamPalettes = next;
    }

    private void saveTeamPalette(int team, EventTeamIconFactory.TeamPalette palette)
    {
        EventTeamIconFactory.TeamPalette p = palette == null
            ? EventTeamIconFactory.defaultPalette(team)
            : palette;
        configManager.setConfiguration(EventListConfig.CONFIG_GROUP, teamColorKey(team), p.getKey());
    }

    public void cycleTeamPalette(int team)
    {
        EventTeamIconFactory.TeamPalette current = teamPalette(team);
        EventTeamIconFactory.TeamPalette[] all = EventTeamIconFactory.TeamPalette.values();
        EventTeamIconFactory.TeamPalette next = all[(current.ordinal() + 1) % all.length];
        saveTeamPalette(team, next);
    }

    public BufferedImage paletteBadge(EventTeamIconFactory.TeamPalette palette)
    {
        EventTeamIconFactory.TeamPalette p = palette == null
            ? EventTeamIconFactory.TeamPalette.RED
            : palette;
        return badgeImages[p.ordinal()];
    }

    private int iconIndexForTeam(int team)
    {
        if (iconIds == null)
        {
            return -1;
        }
        return chatIconManager.chatIconIndex(iconIds[cachedTeamPalette(team).ordinal()]);
    }

    private int friendsIconIndexForTeam(int team)
    {
        if (friendsIconIds == null)
        {
            return -1;
        }
        return chatIconManager.chatIconIndex(friendsIconIds[cachedTeamPalette(team).ordinal()]);
    }

    public void onConfigChanged(String key)
    {
        if (!active)
        {
            return;
        }
        if (key != null && key.endsWith("Color"))
        {
            // Palette icons are fixed assets; only retag so teams pick up the new color key.
            rebuildTeamPalettes();
            clientThread.invokeLater(this::retagChatHistory);
            rebuildFriendsList();
            rebuildClanLists();
            return;
        }
        rebuildPlayerTeams();
        clientThread.invokeLater(this::retagChatHistory);
        rebuildFriendsList();
        rebuildClanLists();
    }

    public void onChatMessage(ChatMessage event)
    {
        if (!active || !config.enableTeamIcons())
        {
            return;
        }
        if (PLAYER_CHAT_TYPES.contains(event.getType()))
        {
            tagMessageNode(event.getMessageNode());
        }
        else if (BROADCAST_TYPES.contains(event.getType()))
        {
            tagBroadcastNode(event.getMessageNode());
        }
    }

    public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        if (!active || !config.enableTeamIcons() || !config.friendsListTeamIcons())
        {
            return;
        }

        switch (event.getEventName())
        {
            case "friendsChatSetText":
            {
                // Script callback args: the RSN being drawn is the top of the object stack.
                Object[] objectStack = client.getObjectStack();
                int objectStackSize = client.getObjectStackSize();
                String rsn = (String) objectStack[objectStackSize - 1];
                Integer team = playerTeams.get(Text.standardize(rsn));
                currentlyLayoutingTeam = null;
                if (team != null)
                {
                    int iconIndex = friendsIconIndexForTeam(team);
                    if (iconIndex != -1)
                    {
                        currentlyLayoutingTeam = team;
                        // Friend Notes: icon after name, then SetPosition pads by 14+1.
                        objectStack[objectStackSize - 1] = rsn + " <img=" + iconIndex + ">";
                    }
                }
                break;
            }
            case "friendsChatSetPosition":
            {
                if (currentlyLayoutingTeam == null)
                {
                    return;
                }
                int[] intStack = client.getIntStack();
                int intStackSize = client.getIntStackSize();
                // Fourth int from the top is the row's x offset; shift it past our icon.
                intStack[intStackSize - 4] += EventTeamIconFactory.FRIENDS_ICON_WIDTH + 1;
                break;
            }
            default:
                break;
        }
    }

    public void onScriptPostFired(ScriptPostFired event)
    {
        if (!active || !config.enableTeamIcons() || !config.clanListTeamIcons())
        {
            return;
        }
        if (event.getScriptId() != ScriptID.CLAN_SIDEPANEL_DRAW)
        {
            return;
        }
        tagMemberList(InterfaceID.ClansSidepanel.PLAYERLIST);
        tagMemberList(InterfaceID.ClansGuestSidepanel.PLAYERLIST);
    }

    /** Who is online right now, gathered on the client thread for the panel. */
    public static final class RosterStatus
    {
        public final Map<Integer, Integer> onlineByTeam;
        /** Standardized names. */
        public final Set<String> onlineNames;
        /** Standardized local player name, empty when logged out. */
        public final String localName;

        RosterStatus(Map<Integer, Integer> onlineByTeam, Set<String> onlineNames, String localName)
        {
            this.onlineByTeam = onlineByTeam;
            this.onlineNames = onlineNames;
            this.localName = localName;
        }
    }

    public void computeOnlineCounts(Consumer<RosterStatus> consumer)
    {
        clientThread.invokeLater(() ->
        {
            Set<String> onlineNames = new HashSet<>();
            Player local = client.getLocalPlayer();
            String localName = local == null || local.getName() == null ? "" : Text.standardize(local.getName());
            if (!localName.isEmpty())
            {
                // You are online even when not in a clan or friends chat.
                onlineNames.add(localName);
            }

            ClanChannel clan = client.getClanChannel();
            if (clan != null)
            {
                for (ClanChannelMember member : clan.getMembers())
                {
                    onlineNames.add(Text.standardize(member.getName()));
                }
            }

            ClanChannel guest = client.getGuestClanChannel();
            if (guest != null)
            {
                for (ClanChannelMember member : guest.getMembers())
                {
                    onlineNames.add(Text.standardize(member.getName()));
                }
            }

            FriendsChatManager friendsChat = client.getFriendsChatManager();
            if (friendsChat != null)
            {
                for (FriendsChatMember member : friendsChat.getMembers())
                {
                    onlineNames.add(Text.standardize(member.getName()));
                }
            }

            Map<Integer, Integer> onlineByTeam = new HashMap<>();
            Map<String, Integer> roster = playerTeams;
            for (Map.Entry<String, Integer> entry : roster.entrySet())
            {
                if (onlineNames.contains(entry.getKey()))
                {
                    onlineByTeam.merge(entry.getValue(), 1, Integer::sum);
                }
            }

            RosterStatus status = new RosterStatus(onlineByTeam, onlineNames, localName);
            SwingUtilities.invokeLater(() -> consumer.accept(status));
        });
    }

    void rebuildPlayerTeams()
    {
        rebuildTeamPalettes();
        Map<String, Integer> nextTeams = new HashMap<>();
        List<String> nextConflicts = new ArrayList<>();
        if (!config.enableTeamIcons())
        {
            playerTeams = Collections.emptyMap();
            conflicts = Collections.emptyList();
            return;
        }

        int teamCount = Math.min(Math.max(config.teamCount(), 1), MAX_TEAMS);
        for (int team = 1; team <= teamCount; team++)
        {
            String names = configManager.getConfiguration(EventListConfig.CONFIG_GROUP, teamNamesKey(team));
            if (names == null || names.isEmpty())
            {
                continue;
            }

            for (String raw : splitNames(names))
            {
                String standardized = Text.standardize(raw);
                if (standardized.isEmpty())
                {
                    continue;
                }
                Integer existing = nextTeams.putIfAbsent(standardized, team);
                if (existing != null && existing != team)
                {
                    nextConflicts.add(raw.trim() + " on team " + existing + " and " + team
                        + " (keeping team " + existing + ")");
                }
            }
        }
        playerTeams = Collections.unmodifiableMap(nextTeams);
        conflicts = Collections.unmodifiableList(nextConflicts);
    }

    /**
     * Split on commas/newlines; strip # and // comments; ignore empty tokens.
     */
    static List<String> splitNames(String text)
    {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty())
        {
            return out;
        }
        for (String line : text.split("[\n\r]+"))
        {
            String cleaned = line;
            int hash = cleaned.indexOf('#');
            if (hash >= 0)
            {
                cleaned = cleaned.substring(0, hash);
            }
            int slash = cleaned.indexOf("//");
            if (slash >= 0)
            {
                cleaned = cleaned.substring(0, slash);
            }
            for (String name : cleaned.split(","))
            {
                String trimmed = name.trim();
                if (!trimmed.isEmpty())
                {
                    out.add(trimmed);
                }
            }
        }
        return out;
    }

    private void ensureIconsRegistered()
    {
        if (iconIds == null)
        {
            iconIds = new int[EventTeamIconFactory.PALETTE_SIZE];
            friendsIconIds = new int[EventTeamIconFactory.PALETTE_SIZE];
            for (EventTeamIconFactory.TeamPalette palette : EventTeamIconFactory.TeamPalette.values())
            {
                BufferedImage chatBadge = EventTeamIconFactory.loadBadge(palette);
                BufferedImage friendsBadge = EventTeamIconFactory.loadFriendsListBadge(palette);
                badgeImages[palette.ordinal()] = chatBadge;
                iconIds[palette.ordinal()] = chatIconManager.registerChatIcon(chatBadge);
                friendsIconIds[palette.ordinal()] = chatIconManager.registerChatIcon(friendsBadge);
            }
        }
        else
        {
            // Same JVM / plugin reload keeps static icon ids — refresh the bitmaps in place.
            updateIconImages();
        }
    }

    private void updateIconImages()
    {
        for (EventTeamIconFactory.TeamPalette palette : EventTeamIconFactory.TeamPalette.values())
        {
            BufferedImage chatBadge = EventTeamIconFactory.loadBadge(palette);
            badgeImages[palette.ordinal()] = chatBadge;
            chatIconManager.updateChatIcon(iconIds[palette.ordinal()], chatBadge);
            if (friendsIconIds != null)
            {
                chatIconManager.updateChatIcon(
                    friendsIconIds[palette.ordinal()],
                    EventTeamIconFactory.loadFriendsListBadge(palette));
            }
        }
    }

    private void tagMemberList(int componentId)
    {
        Widget list = client.getWidget(componentId);
        if (list == null || list.getChildren() == null)
        {
            return;
        }

        for (Widget child : list.getChildren())
        {
            if (child == null)
            {
                continue;
            }

            String text = child.getText();
            if (text == null || text.isEmpty())
            {
                continue;
            }

            String stripped = stripOwnListTags(text);
            String newText = stripped;
            Integer team = playerTeams.get(Text.standardize(stripped));
            if (team != null)
            {
                int iconIndex = friendsIconIndexForTeam(team);
                if (iconIndex != -1)
                {
                    newText = stripped + " <img=" + iconIndex + ">";
                }
            }

            if (!newText.equals(text))
            {
                child.setText(newText);
            }
        }
    }

    private String stripOwnListTags(String text)
    {
        text = stripIconIds(text, iconIds);
        return stripIconIds(text, friendsIconIds);
    }

    private String stripOwnTags(String text)
    {
        return stripIconIds(text, iconIds);
    }

    private String stripIconIds(String text, int[] ids)
    {
        if (ids == null)
        {
            return text;
        }
        for (int id : ids)
        {
            int iconIndex = chatIconManager.chatIconIndex(id);
            if (iconIndex != -1)
            {
                text = text.replace("<img=" + iconIndex + "> ", "");
                text = text.replace(" <img=" + iconIndex + ">", "");
                text = text.replace("<img=" + iconIndex + ">", "");
            }
        }
        return text;
    }

    private void tagMessageNode(MessageNode node)
    {
        String name = node.getName();
        if (name == null || name.isEmpty())
        {
            return;
        }

        String strippedName = stripOwnTags(name);
        Integer team = playerTeams.get(Text.standardize(strippedName));
        String tag = "";
        if (team != null)
        {
            int iconIndex = iconIndexForTeam(team);
            if (iconIndex != -1)
            {
                tag = "<img=" + iconIndex + "> ";
            }
        }

        // Keep any non-ours markup (e.g. ironman), only swap our badge.
        String newName = tag + strippedName;
        if (!newName.equals(name))
        {
            node.setName(newName);
        }
    }

    private void tagBroadcastNode(MessageNode node)
    {
        String value = node.getValue();
        if (value == null || value.isEmpty())
        {
            return;
        }

        String stripped = stripOwnTags(value);
        String newValue = stripped;

        if (config.broadcastTeamIcons())
        {
            Matcher matcher = BROADCAST_PATTERN.matcher(Text.removeTags(stripped));
            if (matcher.find())
            {
                String playerName = matcher.group(1);
                Integer team = playerTeams.get(Text.standardize(playerName));
                if (team != null)
                {
                    int iconIndex = iconIndexForTeam(team);
                    if (iconIndex != -1)
                    {
                        String tag = "<img=" + iconIndex + "> ";
                        int at = stripped.indexOf(playerName);
                        newValue = at >= 0
                            ? stripped.substring(0, at) + tag + stripped.substring(at)
                            : tag + stripped;
                    }
                }
            }
        }

        if (!newValue.equals(value))
        {
            node.setValue(newValue);
        }
    }

    private void retagChatHistory()
    {
        Map<Integer, ChatLineBuffer> chatLineMap = client.getChatLineMap();
        if (chatLineMap == null)
        {
            return;
        }

        boolean removeAll = !active || !config.enableTeamIcons();
        for (ChatLineBuffer buffer : chatLineMap.values())
        {
            if (buffer == null)
            {
                continue;
            }

            for (MessageNode node : buffer.getLines())
            {
                if (node == null)
                {
                    continue;
                }

                if (PLAYER_CHAT_TYPES.contains(node.getType()))
                {
                    if (removeAll)
                    {
                        String name = node.getName();
                        if (name != null && !name.isEmpty())
                        {
                            String stripped = stripOwnTags(name);
                            if (!stripped.equals(name))
                            {
                                node.setName(stripped);
                            }
                        }
                    }
                    else
                    {
                        tagMessageNode(node);
                    }
                }
                else if (BROADCAST_TYPES.contains(node.getType()))
                {
                    if (removeAll)
                    {
                        String value = node.getValue();
                        if (value != null && !value.isEmpty())
                        {
                            String stripped = stripOwnTags(value);
                            if (!stripped.equals(value))
                            {
                                node.setValue(stripped);
                            }
                        }
                    }
                    else
                    {
                        tagBroadcastNode(node);
                    }
                }
            }
        }
    }

    private void rebuildFriendsList()
    {
        clientThread.invokeLater(() ->
        {
            if (client.getGameState() != GameState.LOGGED_IN)
            {
                return;
            }
            client.runScript(
                ScriptID.FRIENDS_UPDATE,
                InterfaceID.Friends.LIST_CONTAINER,
                InterfaceID.Friends.SORT_NAME,
                InterfaceID.Friends.SORT_RECENT,
                InterfaceID.Friends.SORT_WORLD,
                InterfaceID.Friends.SORT_LEGACY,
                InterfaceID.Friends.LIST,
                InterfaceID.Friends.SCROLLBAR,
                InterfaceID.Friends.LOADING,
                InterfaceID.Friends.TOOLTIP
            );
        });
    }

    private void rebuildClanLists()
    {
        clientThread.invokeLater(() ->
        {
            if (client.getGameState() != GameState.LOGGED_IN)
            {
                return;
            }

            Widget w = client.getWidget(InterfaceID.ClansSidepanel.UNIVERSE);
            if (w != null)
            {
                client.runScript(w.getOnVarTransmitListener());
            }

            w = client.getWidget(InterfaceID.ClansGuestSidepanel.UNIVERSE);
            if (w != null)
            {
                client.runScript(w.getOnVarTransmitListener());
            }
        });
    }
}
