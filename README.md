# Ironforged Clan Events

A RuneLite plugin for **Iron Forged** clan events: live calendar from `api.ironforged.gg`, side panel timeline, your seasonal team, optional Seasonal Event Passphrase overlay, and event team badges.

Plugin drop submit was removed on 2026-11-08. It can be restored from git tag `seasonal-submit-v1`.

---

## Features

- **Events API** — Fetches from `https://api.ironforged.gg/plugins/events` (sesh-backed, opt-in with RuneLite’s third-party warning)
- **Side panel timeline** — Day-grouped events with live / today / future / past color tones
- **Seasonal Event Passphrase** — Optional on-screen passphrase (and optional local date/time), movable and resizable. Prefers the API value; config is fallback.
- **Seasonal event** — Opt-in: the Seasonal tab shows the current event and your team; **Open board** shows your team's board in a window (Delve today).
- **Your team** — The Seasonal tab shows the event, your team and its badge, and which teammates are online
- **Event teams** — Paste RSNs per team in the Seasonal tab, or opt in to **Sync team roster from API** (`GET /plugins/seasonal/teams`). Rostered players get their team's clan icon in chat, PMs, broadcasts, friends list, clan list and overhead (overhead icons are hidden in the Wilderness and on PvP worlds)
- **Local times** — API timestamps are UTC and shown in your local timezone

---

## Setup

1. Open RuneLite settings → **Ironforged Clan Events**
2. Enable **Load clan events** (accept the third-party warning)
3. Optional: enable **Use dev API** to hit `https://dev-api.ironforged.gg` instead of production
4. Optional: set **Seasonal Event ID** and **Seasonal Event Passphrase** under **Seasonal Event**
5. Optional: under **Teams**, enable **Sync team roster from API**

No Google Sheet ID is required. The official Iron Forged calendar is loaded from the public plugins API.

---

## Seasonal Event

Under **Seasonal Event** in settings:

- Toggle **Show seasonal event** (off by default; sends your RSN and IP to the API) to show the event card and board window on the Seasonal tab
- Enter **Seasonal Event ID** (which event team roster sync asks for)
- Enter **Seasonal Event Passphrase** (overlay text)
- Toggle **Show Seasonal Event Passphrase** on the game screen
- Optionally show local date & time
- Drag to reposition; resize freely but not smaller than the text

Config values are used when set; otherwise the plugin falls back to values from the API.

---

## Development

See [AGENTS.md](./AGENTS.md) for RuneLite Plugin Hub rules this project follows.

Cross-repo team roster contract for the webpage/API team:  
[`docs/plans/PLUGIN_TEAM_ROSTER_SYNC.md`](../../docs/plans/PLUGIN_TEAM_ROSTER_SYNC.md) (workspace).

```bash
./gradlew.bat runClient
# or
./gradlew.bat test
```

Use a Jagex account login for the dev client:  
https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts

---

## License

BSD 2-Clause. See [LICENSE](./LICENSE).
