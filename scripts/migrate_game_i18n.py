from __future__ import annotations

import sys
from datetime import datetime, timezone

from pymongo import MongoClient

from seed_steam_games import (
    build_steam_localized_payload,
    build_uri,
    collect_categories,
    extract_steam_app_id,
    fetch_app_details,
    find_manual_game_data,
    load_env,
    normalize_category_codes,
    normalize_translations,
)


def resolve_existing_translations(game: dict) -> tuple[dict[str, str], dict[str, str]]:
    current_title = game.get("title")
    current_description = game.get("description")

    title_i18n = normalize_translations(game.get("titleI18n"), current_title)
    description_i18n = normalize_translations(game.get("descriptionI18n"), current_description)

    manual_data = find_manual_game_data(current_title)
    if manual_data is None:
        for candidate in title_i18n.values():
            manual_data = find_manual_game_data(candidate)
            if manual_data is not None:
                break

    if manual_data is not None:
        title_i18n = normalize_translations(manual_data.get("titleI18n"), next(iter(title_i18n.values()), current_title))
        description_i18n = normalize_translations(
            manual_data.get("descriptionI18n"),
            next(iter(description_i18n.values()), current_description),
        )

    return title_i18n, description_i18n


def resolve_categories(game: dict, app_data_en: dict | None) -> list[str]:
    if app_data_en is not None:
        return collect_categories(app_data_en)

    manual_data = find_manual_game_data(game.get("title"))
    if manual_data is not None:
        return normalize_category_codes(manual_data.get("categories"))

    existing_categories = game.get("categories")
    categories = existing_categories if isinstance(existing_categories, list) else [game.get("category")]
    return normalize_category_codes(categories)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")

    env = load_env()
    client = MongoClient(build_uri(env))
    collection = client[env["MONGO_DATABASE"]]["games"]

    updated_titles: list[str] = []
    skipped_titles: list[str] = []

    for game in collection.find({}):
        title_i18n, description_i18n = resolve_existing_translations(game)
        app_id = extract_steam_app_id(game.get("downloadLink"))
        app_data_en = None

        if app_id is not None:
            try:
                app_data_en = fetch_app_details(app_id, "english")
                app_data_zh = fetch_app_details(app_id, "schinese")
                title_i18n, description_i18n, categories = build_steam_localized_payload(
                    app_data_en,
                    app_data_zh,
                    next(iter(title_i18n.values()), game.get("title")),
                    next(iter(description_i18n.values()), game.get("description")),
                )
            except Exception:
                categories = resolve_categories(game, None)
        else:
            categories = resolve_categories(game, None)

        payload = {
            "titleI18n": title_i18n,
            "descriptionI18n": description_i18n,
            "categories": categories,
            "updatedAt": datetime.now(timezone.utc),
        }

        result = collection.update_one(
            {"_id": game["_id"]},
            {
                "$set": payload,
                "$unset": {"title": "", "description": "", "category": ""},
            },
        )

        game_name = title_i18n.get("zh-CN") or title_i18n.get("en-US") or str(game["_id"])
        if result.modified_count > 0:
            updated_titles.append(game_name)
        else:
            skipped_titles.append(game_name)

    print("Updated titles:")
    for title in updated_titles:
        print(f"  - {title}")

    print("Skipped titles:")
    for title in skipped_titles:
        print(f"  - {title}")

    print(f"Total updated: {len(updated_titles)}")
    print(f"Total skipped: {len(skipped_titles)}")


if __name__ == "__main__":
    main()
