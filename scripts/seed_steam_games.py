from __future__ import annotations

import json
import ssl
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from pymongo import MongoClient


APP_IDS = [
    1086940,
    1091500,
    1245620,
    1145360,
    367520,
    1426210,
    1174180,
    814380,
    553850,
    990080,
    1716740,
    1817070,
    1938090,
    1172470,
    1085660,
    730,
    271590,
    570,
    550,
    620,
    394360,
    413150,
    2878980,
    275850,
    359550,
]

REQUEST_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/123.0.0.0 Safari/537.36"
    )
}

LABEL_MAP = {
    "rpg": "角色扮演",
    "action": "动作",
    "adventure": "冒险",
    "shooter": "射击",
    "fps": "射击",
    "strategy": "策略",
    "simulation": "模拟",
    "sports": "体育",
    "racing": "竞速",
    "indie": "独立游戏",
    "casual": "休闲",
    "survival": "生存",
    "horror": "恐怖",
    "puzzle": "解谜",
    "turn-based": "回合制",
    "sandbox": "沙盒",
    "open world": "开放世界",
    "single-player": "单人",
    "multi-player": "多人在线",
    "co-op": "合作",
    "online co-op": "合作",
    "local co-op": "合作",
}


def load_env() -> dict[str, str]:
    env_path = Path("src/main/resources/.env")
    env: dict[str, str] = {}

    for line in env_path.read_text(encoding="utf-8").splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        env[key.strip()] = value.strip()

    return env


def build_uri(env: dict[str, str]) -> str:
    return (
        f"mongodb+srv://{env['MONGO_USERNAME']}:{env['MONGO_PASSWORD']}"
        f"@{env['MONGO_CLUSTER']}/{env['MONGO_DATABASE']}?retryWrites=true&w=majority"
    )


def fetch_json(url: str, retries: int = 3) -> dict:
    last_error: Exception | None = None
    context = ssl.create_default_context()

    for attempt in range(retries):
        try:
            request = urllib.request.Request(url, headers=REQUEST_HEADERS)
            with urllib.request.urlopen(request, timeout=25, context=context) as response:
                return json.load(response)
        except Exception as exc:
            last_error = exc
            time.sleep(1.2 * (attempt + 1))

    raise RuntimeError(f"request failed: {last_error}")


def fetch_app_details(app_id: int) -> dict:
    payload = fetch_json(
        f"https://store.steampowered.com/api/appdetails?appids={app_id}&l=english&cc=us"
    )
    app_payload = payload.get(str(app_id), {})
    if not app_payload.get("success"):
        raise ValueError(f"Steam app {app_id} not available")
    return app_payload["data"]


def fetch_review_rating(app_id: int) -> float | None:
    params = urllib.parse.urlencode(
        {
            "json": 1,
            "language": "all",
            "purchase_type": "all",
            "num_per_page": 0,
        }
    )
    payload = fetch_json(f"https://store.steampowered.com/appreviews/{app_id}?{params}")
    summary = payload.get("query_summary") or {}
    positive = summary.get("total_positive") or 0
    negative = summary.get("total_negative") or 0
    total = positive + negative

    if total == 0:
        return None

    return round((positive / total) * 10, 1)


def parse_release_date(raw: str) -> datetime:
    raw = raw.strip()
    for pattern in ("%d %b, %Y", "%b %d, %Y", "%d %B, %Y", "%B %d, %Y", "%Y"):
        try:
            parsed = datetime.strptime(raw, pattern)
            month = parsed.month if "%d" in pattern or "%b" in pattern or "%B" in pattern else 1
            day = parsed.day if "%d" in pattern else 1
            return datetime(parsed.year, month, day, tzinfo=timezone.utc)
        except ValueError:
            continue

    raise ValueError(f"Unsupported release date format: {raw}")


def collect_categories(data: dict) -> list[str]:
    labels: list[str] = []

    genre_names = [genre.get("description", "") for genre in data.get("genres") or []]
    category_names = [category.get("description", "") for category in data.get("categories") or []]
    all_names = [name.lower() for name in [*genre_names, *category_names] if name]

    for keyword, label in LABEL_MAP.items():
        if any(keyword in name for name in all_names) and label not in labels:
            labels.append(label)

    if not labels:
      return ["未分类"]

    return labels


def build_game_document(data: dict) -> dict:
    release_date = parse_release_date(data["release_date"]["date"])
    metacritic = data.get("metacritic", {}).get("score")
    review_rating = fetch_review_rating(data["steam_appid"])
    movies = data.get("movies") or []
    trailer_url = ""

    if movies:
        trailer_url = (
            movies[0].get("mp4", {}).get("max")
            or movies[0].get("webm", {}).get("max")
            or ""
        )

    now = datetime.now(timezone.utc)
    return {
        "title": data["name"],
        "description": data.get("short_description") or "No description available.",
        "coverImage": data.get("header_image", ""),
        "cinematicTrailer": trailer_url,
        "downloadLink": f"https://store.steampowered.com/app/{data['steam_appid']}/",
        "rating": round(metacritic / 10, 1) if metacritic is not None else (review_rating or 8.0),
        "categories": collect_categories(data),
        "releaseDate": release_date,
        "updatedAt": now,
        "createdAt": now,
    }


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")

    env = load_env()
    client = MongoClient(build_uri(env))
    collection = client[env["MONGO_DATABASE"]]["games"]

    inserted: list[str] = []
    updated: list[str] = []
    skipped: list[tuple[int, str]] = []

    for app_id in APP_IDS:
        try:
            app_data = fetch_app_details(app_id)
            document = build_game_document(app_data)
        except Exception as exc:
            skipped.append((app_id, f"fetch_failed: {exc}"))
            continue

        created_at = document.pop("createdAt")
        result = collection.update_one(
            {"title": document["title"]},
            {
                "$set": document,
                "$unset": {"category": ""},
                "$setOnInsert": {"createdAt": created_at},
            },
            upsert=True,
        )

        if result.upserted_id is not None:
            inserted.append(document["title"])
        elif result.modified_count > 0:
            updated.append(document["title"])
        else:
            skipped.append((app_id, f"unchanged: {document['title']}"))

    print("Inserted:")
    for title in inserted:
        print(f"  - {title}")

    print("Updated:")
    for title in updated:
        print(f"  - {title}")

    print("Skipped:")
    for app_id, reason in skipped:
        print(f"  - {app_id}: {reason}")

    print(f"Total inserted: {len(inserted)}")
    print(f"Total updated: {len(updated)}")


if __name__ == "__main__":
    main()
