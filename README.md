# Clan Event List

A RuneLite plugin that displays clan events from a Google Sheet with an in-game overlay and side panel.

---

## Features

- **Google Sheets Integration** - Fetch event data from any published Google Sheet
- **In-Game Overlay** - Shows the next upcoming event with countdown timer
- **Side Panel** - Scrollable list of all upcoming events with full details
- **Subpanels** - Switch between `Events` and `Seasonal` tabs inside the panel
- **Auto-Refresh** - Automatically updates event data at configurable intervals
- **Customizable** - Configure colors, event limits, and display options
- **Event Status** - Visual highlighting for imminent and currently happening events
- **Link Support** - Add URLs to events for Discord links, sign-up forms, etc.
- **Seasonal Reporter (Optional)** - Captures boss loot and submits to Iron Forged seasonal backend with queue/retry
- **Seasonal Debug Controls** - Queue fake drop, test API, refresh manifest, and flush queue from panel

---

## Google Sheet Setup

### Step 1: Create Your Sheet

Create a Google Sheet with these columns (in order):

| Column | Field | Required | Example |
|:------:|:------|:--------:|:--------|
| A | Event Name | ✅ | Bossing Night |
| B | Description | ❌ | Weekly group bossing |
| C | Date | ✅ | 12-15-25 |
| D | Start Time (UTC) | ✅ | 20:00 |
| E | End Time (UTC) | ❌ | 22:00 |
| F | Host | ❌ | PlayerName |
| G | Location | ❌ | Chambers of Xeric |
| H | URL | ❌ | https://discord.gg/... |

> ⚠️ **Important:** All times must be in **UTC timezone**. The plugin automatically converts to each user's local timezone.

<br>

**Example Sheet:** *(times in UTC)*

| Event Name | Description | Date | Start | End | Host | Location | URL |
|:-----------|:------------|:----:|:-----:|:---:|:-----|:---------|:----|
| Bossing Night | Weekly GWD runs | 12-15-25 | 20:00 | 22:00 | John | GWD | |
| PvM Training | Learner raids | 12-16-25 | 19:00 | 21:30 | Jane | CoX | https://... |

<br>

### Step 2: Publish Your Sheet

1. Open your Google Sheet
2. Go to **File → Share → Publish to web**
3. Select the sheet tab you want to publish
4. Choose **Comma-separated values (.csv)**
5. Click **Publish**

<br>

### Step 3: Get Your Sheet ID

Your Sheet ID is in the URL:

```
https://docs.google.com/spreadsheets/d/YOUR_SHEET_ID_HERE/edit
```

Copy the long string between `/d/` and `/edit`.

<br>

### Step 4: Configure the Plugin

1. Open RuneLite settings
2. Find **Clan Event List**
3. Enter your **Google Sheet ID**
4. *(Optional)* Enter the **Sheet Tab Name** if not using the first tab

---

## Configuration Options

### Google Sheet Settings

| Setting | Description |
|:--------|:------------|
| Google Sheet ID | The ID from your Google Sheet URL |
| Sheet Tab Name | Name of the specific tab (leave empty for first sheet) |
| Refresh Interval | How often to fetch new data (1-60 minutes) |

<br>

### Overlay Settings

| Setting | Description |
|:--------|:------------|
| Show Overlay | Toggle overlay visibility |
| Background Color | Customize overlay background |
| Text Color | Customize text color |
| Accent Color | Color for event highlights |
| Imminent Event Color | Color when event starts within 1 hour |
| Active Event Color | Color for currently happening events |

<br>

### Panel Settings

| Setting | Description |
|:--------|:------------|
| Max Events Shown | Limit events displayed (1-50) |
| Show Past Events | Include completed events |

<br>

### Seasonal Reporter Settings

| Setting | Description |
|:--------|:------------|
| Enable Seasonal Reporter | Opt-in data submission for seasonal drops |
| Dry Run Mode | Capture and log candidates without API calls |
| Connect Code | One-time admin-issued link code |
| Link/Re-link | Exchanges connect code for secure linked session |
| Test API | Runs backend state check with linked identity |
| Flush Queue | Immediately attempts sending queued submissions |

---

## Supported Formats

### Dates

| Format | Example |
|:-------|:--------|
| ISO (recommended) | `2025-12-15` |
| US | `12-15-25` or `12/15/25` |
| EU | `15/12/2025` |

### Times

| Format | Example |
|:-------|:--------|
| 24-hour | `20:00` |
| 12-hour | `8:00 PM` |

---

## Troubleshooting

### Events not loading?

1. ✅ Ensure your sheet is published to the web
2. ✅ Verify the Sheet ID is correct
3. ✅ Check column order matches the expected format
4. ✅ First row must be a header row

### Wrong times displayed?

Times in the Google Sheet must be in **UTC**. The plugin converts them to your local timezone automatically.

Use a [UTC Time Converter](https://www.timeanddate.com/worldclock/converter.html) when entering event times.

### Seasonal reporter not sending?

1. Ensure **Enable Seasonal Reporter** is enabled
2. Use **Link/Re-link** after entering a valid connect code
3. Keep **Dry Run Mode** off for real submissions
4. Check panel footer tooltip for `Last API` and `Last error`

---

## Seasonal Linking Flow

1. Admin generates a one-time connect code from Iron Forged website/admin tools.
2. Player enters connect code in plugin config and clicks **Link/Re-link**.
3. Plugin calls `POST /clan/seasonal-event/plugin/bootstrap`.
4. Backend returns locked identity (`event_id`, `player_discord_id`, `team_id`) plus opaque `session_token`.
5. Plugin stores only opaque token + server identity metadata locally and uses that identity for all submissions.

No editable local drop list, team id, event id, or scoring rules are used by the plugin. Backend remains source of truth.

---

## Seasonal Queue and Retry

- Captured loot is enqueued locally and persisted to RuneLite directory.
- Queue is processed in background every 10 seconds.
- Bulk endpoint is preferred; plugin falls back to single submission endpoint when bulk is unavailable.
- Retry uses exponential backoff on transient failures.
- Validation failures are moved to dead-letter storage.
- 401/403 pauses sending and requires re-link.
- Optional eligibility manifest can be fetched from backend state for local prefiltering.

### Eligibility Manifest (Zero-Trust Friendly)

- Plugin can fetch server configuration/eligibility from `GET /clan/seasonal-event/state`.
- If server includes eligibility arrays (boss/item lists), plugin uses them as read-only local prefilter.
- Local prefilter is optimization only; backend is still final authority for acceptance/rejection.
- No editable local file is used to define trusted scoring or team identity.

---

## Manual Test Plan

1. **Dry-run smoke test**
   - Enable seasonal reporter + dry run, kill an NPC with loot.
   - Expect: no network submission, panel seasonal status updates, no queue growth.
2. **Link and submit**
   - Enter valid connect code, click Link/Re-link, disable dry run, kill eligible boss.
   - Expect: queued item appears then drains after scheduler run.
3. **Retry behavior**
   - Disconnect network temporarily and trigger drops.
   - Expect: queued items stay queued and retry with backoff.
4. **Auth expiry**
   - Invalidate session server-side and trigger queue processing.
   - Expect: auth pause + re-link warning.
5. **Deduplication safety**
   - Trigger repeated duplicate drop captures in same hour window.
   - Expect: local duplicate filtering for same dedupe key.

---

## License

BSD 2-Clause License - See [LICENSE](LICENSE) for details.
