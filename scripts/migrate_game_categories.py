from __future__ import annotations

import re
import sys
from pathlib import Path

from pymongo import MongoClient

from seed_steam_games import build_uri, collect_categories, fetch_app_details, load_env


MANUAL_CATEGORIES = {
    "塞尔达传说：旷野之息": ["动作", "冒险", "开放世界", "单人"],
    "巫师 3：狂猎": ["角色扮演", "冒险", "开放世界", "单人"],
    "文明 6": ["策略", "模拟", "回合制", "单人"],
    "FIFA 23": ["体育", "模拟", "多人在线"],
    "CS:GO": ["射击", "动作", "多人在线"],
    "博德之门3": ["角色扮演", "冒险", "策略", "单人"],
    "双人成行": ["动作", "冒险", "合作"],
    "艾尔登法环": ["角色扮演", "动作", "冒险", "开放世界", "单人"],
    "赛博朋克 2077": ["角色扮演", "动作", "开放世界", "单人"],
    "霍格沃茨之遗": ["角色扮演", "动作", "冒险", "开放世界", "单人"],
}


def extract_steam_app_id(download_link: str | None) -> int | None:
    if not download_link:
        return None

    match = re.search(r"/app/(\d+)", download_link)
    if not match:
        return None

    return int(match.group(1))


def unique_labels(labels: list[str]) -> list[str]:
    result: list[str] = []
    for label in labels:
        if label and label not in result:
            result.append(label)
    return result or ["未分类"]


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")

    env = load_env()
    client = MongoClient(build_uri(env))
    collection = client[env["MONGO_DATABASE"]]["games"]

    updated_titles: list[str] = []
    skipped_titles: list[str] = []

    for game in collection.find({}):
        title = game.get("title", "")
        categories = MANUAL_CATEGORIES.get(title)

        if categories is None:
            app_id = extract_steam_app_id(game.get("downloadLink"))
            if app_id is not None:
                try:
                    app_data = fetch_app_details(app_id)
                    categories = collect_categories(app_data)
                except Exception:
                    categories = None

        if categories is None:
            old_category = game.get("category")
            old_categories = game.get("categories")
            if isinstance(old_categories, list) and old_categories:
                categories = old_categories
            elif old_category:
                categories = [old_category]
            else:
                categories = ["未分类"]

        categories = unique_labels(categories)

        result = collection.update_one(
            {"_id": game["_id"]},
            {"$set": {"categories": categories}, "$unset": {"category": ""}},
        )

        if result.modified_count > 0:
            updated_titles.append(title)
        else:
            skipped_titles.append(title)

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
