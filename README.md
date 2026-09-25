# Ironforged Clan Events

A RuneLite plugin for **Iron Forged** clan events: live calendar from `api.ironforged.gg`, side panel timeline, optional Seasonal Event Passphrase overlay, and opt-in notable seasonal drop submit.

---

## Features

- **Events API** — Fetches from `https://api.ironforged.gg/plugins/events` (sesh-backed, opt-in with RuneLite’s third-party warning)
- **Side panel timeline** — Day-grouped events with live / today / future / past color tones
- **Seasonal Event Passphrase** — Optional on-screen passphrase (and optional local date/time), movable and resizable. Prefers the API value; config is fallback. Same value authenticates drop submits.
- **Notable drop submit** — Opt-in: only catalog-listed boss/item drops are screenshotted and sent to `POST /plugins/seasonal/drops` with **Seasonal Event ID** + **Seasonal Event Passphrase** (common loot never uploads)
- **Event teams** — Paste RSNs per team in the Seasonal tab, or opt in to **Sync team roster from API** (`GET /plugins/seasonal/teams`). Rostered players get their team's clan icon in chat, PMs, broadcasts, friends list, clan list and overhead (overhead icons are hidden in the Wilderness and on PvP worlds)
- **Local times** — API timestamps are UTC and shown in your local timezone

---

## Setup

1. Open RuneLite settings → **Ironforged Clan Events**
2. Enable **Load clan events** (accept the third-party warning)
3. Optional: enable **Use dev API** to hit `https://dev-api.ironforged.gg` instead of production
4. Optional: set **Seasonal Event ID** and **Seasonal Event Passphrase** under **Seasonal Event**
5. Optional: under **Seasonal Drop Submit**, enable submit

No Google Sheet ID is required. The official Iron Forged calendar is loaded from the public plugins API.

---

## Seasonal Event

Under **Seasonal Event** in settings:

- Enter **Seasonal Event ID** (which event drops count toward)
- Enter **Seasonal Event Passphrase** (overlay text + submit auth)
- Toggle **Show Seasonal Event Passphrase** on the game screen
- Optionally show local date & time
- Drag to reposition; resize freely but not smaller than the text

Config values are used when set; otherwise the plugin falls back to values from the API.

---

## Seasonal drop submit

Under **Seasonal Drop Submit**:

- Enable **Submit notable seasonal drops** (third-party warning)
- Ensure **Seasonal Event ID** and **Seasonal Event Passphrase** are set (config and/or API)
- The plugin loads the notable catalog from `GET /plugins/seasonal` and ignores every other drop locally
- Each submit includes **Seasonal Event ID** + **Seasonal Event Passphrase**
- Optional **Dry run** matches notables without uploading
- Raid, Barrows, Gauntlet and other chest drops are only seen when RuneLite's **Loot Tracker** plugin is enabled
- Failed uploads are queued on disk and retried every minute for up to 24 hours
- The Seasonal tab has **View allowed drops** and your recent submits
- The Debug tab has **Refresh catalog** and **Test submit** (posts the first allowed catalog entry to the dev API, `dev-api.ironforged.gg`, never prod)

---

## Development

See [AGENTS.md](./AGENTS.md) for RuneLite Plugin Hub rules this project follows.

Cross-repo submit contract for the webpage/API team:  
[`docs/plans/PLUGIN_SEASONAL_SUBMIT_FLOW.md`](../../docs/plans/PLUGIN_SEASONAL_SUBMIT_FLOW.md) (workspace).

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
