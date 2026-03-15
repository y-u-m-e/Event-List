import datetime
import json
import os
import re


AGENT_TOOLS_DIR = r"C:\Users\Karl\.cursor\projects\o-OSRS-CLAN-webpage\agent-tools"
COLLECTION_PAGE_FILE = os.path.join(AGENT_TOOLS_DIR, "8c0e4715-0675-47de-9488-c2b5338400b2.txt")
COLLECTION_DATA_FILE = os.path.join(AGENT_TOOLS_DIR, "add94d25-a704-4d0d-a407-aa76df5cc94f.txt")
OUTPUT_PATH = r"o:\OSRS_CLAN\plugin\Event-List\data\osrs_wiki_boss_collection_log_uniques.json"


def load_wiki_content(api_response_path: str) -> str:
    with open(api_response_path, "r", encoding="utf-8") as f:
        payload = json.load(f)
    return payload["query"]["pages"][0]["revisions"][0]["slots"]["main"]["content"]


def parse_boss_tabs(collection_log_wikitext: str) -> list[str]:
    start = collection_log_wikitext.find("==Bosses==")
    if start < 0:
        return []
    m = re.search(r"\n==[^=].*?==", collection_log_wikitext[start + 10 :], flags=re.S)
    end = (start + 10 + m.start()) if m else -1
    section = collection_log_wikitext[start : (end if end != -1 else len(collection_log_wikitext))]
    return [m.group(1).strip() for m in re.finditer(r"^===\s*(.*?)\s*===\s*$", section, flags=re.M)]


def build_output() -> dict:
    collection_log_wikitext = load_wiki_content(COLLECTION_PAGE_FILE)
    data_json_text = load_wiki_content(COLLECTION_DATA_FILE)
    data_rows = json.loads(data_json_text)

    boss_tabs = parse_boss_tabs(collection_log_wikitext)
    by_tab: dict[str, list[dict]] = {tab: [] for tab in boss_tabs}
    by_tab_lower = {tab.lower(): tab for tab in boss_tabs}

    for row in data_rows:
        item_name = str(row.get("name", "")).strip()
        item_id = row.get("id")
        for tab in row.get("tabs") or []:
            tab_key = by_tab_lower.get(str(tab).strip().lower())
            if tab_key:
                by_tab[tab_key].append(
                    {
                        "item_id": item_id,
                        "item_name": item_name,
                    }
                )

    bosses = []
    total_items = 0
    for boss in boss_tabs:
        unique_items = []
        seen = set()
        for item in by_tab[boss]:
            key = (item["item_id"], item["item_name"])
            if key in seen:
                continue
            seen.add(key)
            unique_items.append(item)

        unique_items.sort(key=lambda x: (str(x["item_name"]).lower(), int(x["item_id"])))
        bosses.append({"boss": boss, "count": len(unique_items), "unique_items": unique_items})
        total_items += len(unique_items)

    empty_tabs = [tab for tab, rows in by_tab.items() if not rows]

    return {
        "source": "https://oldschool.runescape.wiki",
        "generated_at_utc": datetime.datetime.now(datetime.UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "note": (
            "Boss names are parsed from the Collection log Bosses section. "
            "Items are sourced from Module:Collection_log/data.json where tabs match boss names."
        ),
        "boss_count": len(bosses),
        "item_count": total_items,
        "empty_boss_tabs": empty_tabs,
        "bosses": bosses,
    }


def main() -> None:
    output = build_output()
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=True)
    print(f"Wrote {OUTPUT_PATH}")
    print(f"Bosses: {output['boss_count']}, Items: {output['item_count']}")


if __name__ == "__main__":
    main()
