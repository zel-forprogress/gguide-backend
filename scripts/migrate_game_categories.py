from __future__ import annotations

import sys

from pymongo import MongoClient

from seed_steam_games import build_uri, load_env, normalize_category_codes


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")

    env = load_env()
    client = MongoClient(build_uri(env))
    collection = client[env["MONGO_DATABASE"]]["games"]

    updated = 0

    for game in collection.find({}):
      existing_categories = game.get("categories")
      categories = existing_categories if isinstance(existing_categories, list) else [game.get("category")]
      normalized_categories = normalize_category_codes(categories)

      result = collection.update_one(
          {"_id": game["_id"]},
          {"$set": {"categories": normalized_categories}, "$unset": {"category": ""}},
      )
      if result.modified_count > 0:
          updated += 1

    print(f"Total updated: {updated}")


if __name__ == "__main__":
    main()
