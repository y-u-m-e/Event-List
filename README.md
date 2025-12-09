# Clan Event List

A RuneLite plugin that displays clan events from a Google Sheet with an in-game overlay and side panel.

---

## Features

- **Google Sheets Integration** - Fetch event data from any published Google Sheet
- **In-Game Overlay** - Shows the next upcoming event with countdown timer
- **Side Panel** - Scrollable list of all upcoming events with full details
- **Auto-Refresh** - Automatically updates event data at configurable intervals
- **Customizable** - Configure colors, event limits, and display options
- **Event Status** - Visual highlighting for imminent and currently happening events
- **Link Support** - Add URLs to events for Discord links, sign-up forms, etc.

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

---

## License

BSD 2-Clause License - See [LICENSE](LICENSE) for details.
