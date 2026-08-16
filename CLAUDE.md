# Ironforged Clan Events (Event-List)

RuneLite plugin (`com.claneventlist`). Hub checklist lives in `AGENTS.md` and `.cursor/rules/plugin-hub.mdc` — read those before editing.

- Java 11. OkHttp + injected Gson. No client-thread IO. No reflection / ProcessBuilder.
- Third-party HTTP (yume-api, Sheets) must stay opt-in with the Hub IP warning.
- Do not automate RuneScape input. Offer `./gradlew.bat run` or `runClient`, tell the user what to test, wait.
- Do not tidy `plugin/plugin-hub`. Do not edit ironforged-pages / yume-api in the same sitting.
