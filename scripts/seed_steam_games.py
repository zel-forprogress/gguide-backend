from __future__ import annotations

import json
import re
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

CATEGORY_KEYWORDS = {
    "rpg": "RPG",
    "role-playing": "RPG",
    "action": "ACTION",
    "adventure": "ADVENTURE",
    "shooter": "SHOOTER",
    "fps": "SHOOTER",
    "strategy": "STRATEGY",
    "simulation": "SIMULATION",
    "sports": "SPORTS",
    "racing": "RACING",
    "indie": "INDIE",
    "casual": "CASUAL",
    "survival": "SURVIVAL",
    "horror": "HORROR",
    "puzzle": "PUZZLE",
    "turn-based": "TURN_BASED",
    "sandbox": "SANDBOX",
    "open world": "OPEN_WORLD",
    "single-player": "SINGLE_PLAYER",
    "single player": "SINGLE_PLAYER",
    "multi-player": "MULTIPLAYER",
    "multiplayer": "MULTIPLAYER",
    "online pvp": "MULTIPLAYER",
    "mmo": "MULTIPLAYER",
    "co-op": "COOP",
    "co op": "COOP",
    "online co-op": "COOP",
    "online co op": "COOP",
    "local co-op": "COOP",
    "local co op": "COOP",
}

CATEGORY_ALIASES = {
    "action": "ACTION",
    "动作": "ACTION",
    "adventure": "ADVENTURE",
    "冒险": "ADVENTURE",
    "rpg": "RPG",
    "角色扮演": "RPG",
    "shooter": "SHOOTER",
    "射击": "SHOOTER",
    "strategy": "STRATEGY",
    "策略": "STRATEGY",
    "simulation": "SIMULATION",
    "模拟": "SIMULATION",
    "sports": "SPORTS",
    "体育": "SPORTS",
    "racing": "RACING",
    "竞速": "RACING",
    "indie": "INDIE",
    "独立游戏": "INDIE",
    "casual": "CASUAL",
    "休闲": "CASUAL",
    "survival": "SURVIVAL",
    "生存": "SURVIVAL",
    "horror": "HORROR",
    "恐怖": "HORROR",
    "puzzle": "PUZZLE",
    "解谜": "PUZZLE",
    "turn_based": "TURN_BASED",
    "turn-based": "TURN_BASED",
    "回合制": "TURN_BASED",
    "sandbox": "SANDBOX",
    "沙盒": "SANDBOX",
    "open_world": "OPEN_WORLD",
    "open world": "OPEN_WORLD",
    "开放世界": "OPEN_WORLD",
    "single_player": "SINGLE_PLAYER",
    "single player": "SINGLE_PLAYER",
    "单人": "SINGLE_PLAYER",
    "multiplayer": "MULTIPLAYER",
    "multi-player": "MULTIPLAYER",
    "多人在线": "MULTIPLAYER",
    "coop": "COOP",
    "co-op": "COOP",
    "合作": "COOP",
}

MANUAL_GAME_DATA = {
    "塞尔达传说：旷野之息": {
        "titleI18n": {
            "zh-CN": "塞尔达传说：旷野之息",
            "en-US": "The Legend of Zelda: Breath of the Wild",
        },
        "descriptionI18n": {
            "zh-CN": "在海拉鲁大陆自由探索，攀登高山、解开谜题，并以自己的节奏书写冒险。",
            "en-US": "Wake up in Hyrule and explore a vast open world filled with puzzles, climbing, and player-driven adventure.",
        },
        "categories": ["ACTION", "ADVENTURE", "OPEN_WORLD", "SINGLE_PLAYER"],
    },
    "巫师 3：狂猎": {
        "titleI18n": {
            "zh-CN": "巫师 3：狂猎",
            "en-US": "The Witcher 3: Wild Hunt",
        },
        "descriptionI18n": {
            "zh-CN": "扮演猎魔人杰洛特，在战火纷飞的大陆寻找希里，并参与一个充满抉择的史诗故事。",
            "en-US": "Play as Geralt of Rivia and hunt for Ciri across a war-torn continent in a sprawling story-driven RPG.",
        },
        "categories": ["RPG", "ADVENTURE", "OPEN_WORLD", "SINGLE_PLAYER"],
    },
    "CS:GO": {
        "titleI18n": {
            "zh-CN": "反恐精英：全球攻势",
            "en-US": "Counter-Strike: Global Offensive",
        },
        "descriptionI18n": {
            "zh-CN": "经典的团队战术射击体验，攻守双方围绕目标展开高强度对抗。",
            "en-US": "A classic team-based tactical shooter built around high-stakes objective play.",
        },
        "categories": ["SHOOTER", "ACTION", "MULTIPLAYER"],
    },
    "文明 6": {
        "titleI18n": {
            "zh-CN": "文明 6",
            "en-US": "Sid Meier's Civilization VI",
        },
        "descriptionI18n": {
            "zh-CN": "从远古时代一路发展到信息时代，建设文明、制定策略，并与世界强国竞争。",
            "en-US": "Build an empire from the ancient era to the information age in a turn-based strategy sandbox.",
        },
        "categories": ["STRATEGY", "SIMULATION", "TURN_BASED", "SINGLE_PLAYER"],
    },
    "FIFA 23": {
        "titleI18n": {
            "zh-CN": "FIFA 23",
            "en-US": "FIFA 23",
        },
        "descriptionI18n": {
            "zh-CN": "体验足球赛场的速度与对抗，参与真实联赛、俱乐部与多人竞技。",
            "en-US": "Step onto the pitch with licensed clubs, leagues, and competitive multiplayer football action.",
        },
        "categories": ["SPORTS", "SIMULATION", "MULTIPLAYER"],
    },
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


def fetch_app_details(app_id: int, language: str = "english") -> dict:
    payload = fetch_json(
        f"https://store.steampowered.com/api/appdetails?appids={app_id}&l={language}&cc=us"
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


def extract_steam_app_id(download_link: str | None) -> int | None:
    if not download_link:
        return None

    match = re.search(r"/app/(\d+)", download_link)
    if not match:
        return None

    return int(match.group(1))


def normalize_category_codes(raw_categories: list[str] | None) -> list[str]:
    normalized: list[str] = []
    for raw_category in raw_categories or []:
        if not raw_category:
            continue

        key = raw_category.strip()
        if not key:
            continue

        code = CATEGORY_ALIASES.get(key.lower())
        if code is None:
            code = key.upper().replace("-", "_").replace(" ", "_")

        if code not in normalized:
            normalized.append(code)

    return normalized or ["UNCATEGORIZED"]


def collect_categories(data: dict) -> list[str]:
    labels: list[str] = []

    genre_names = [genre.get("description", "") for genre in data.get("genres") or []]
    category_names = [category.get("description", "") for category in data.get("categories") or []]
    all_names = [name.lower() for name in [*genre_names, *category_names] if name]

    for keyword, code in CATEGORY_KEYWORDS.items():
        if any(keyword in name for name in all_names) and code not in labels:
            labels.append(code)

    return normalize_category_codes(labels)


def normalize_translations(values: dict[str, str] | None, fallback: str | None = None) -> dict[str, str]:
    normalized: dict[str, str] = {}
    for locale in ("zh-CN", "en-US"):
        value = (values or {}).get(locale)
        if value and value.strip():
            normalized[locale] = value.strip()

    if fallback and fallback.strip():
        normalized.setdefault("zh-CN", fallback.strip())
        normalized.setdefault("en-US", fallback.strip())

    return normalized


def build_steam_localized_payload(
    details_en: dict,
    details_zh: dict,
    fallback_title: str | None = None,
    fallback_description: str | None = None,
) -> tuple[dict[str, str], dict[str, str], list[str]]:
    title_i18n = normalize_translations(
        {
            "en-US": details_en.get("name", ""),
            "zh-CN": details_zh.get("name", ""),
        },
        fallback_title,
    )
    description_i18n = normalize_translations(
        {
            "en-US": details_en.get("short_description", ""),
            "zh-CN": details_zh.get("short_description", ""),
        },
        fallback_description,
    )
    categories = collect_categories(details_en)
    return title_i18n, description_i18n, categories


def build_game_document(details_en: dict, details_zh: dict) -> dict:
    release_date = parse_release_date(details_en["release_date"]["date"])
    metacritic = details_en.get("metacritic", {}).get("score")
    review_rating = fetch_review_rating(details_en["steam_appid"])
    movies = details_en.get("movies") or []
    trailer_url = ""

    if movies:
        trailer_url = (
            movies[0].get("mp4", {}).get("max")
            or movies[0].get("webm", {}).get("max")
            or ""
        )

    title_i18n, description_i18n, categories = build_steam_localized_payload(details_en, details_zh)
    now = datetime.now(timezone.utc)

    return {
        "titleI18n": title_i18n,
        "descriptionI18n": description_i18n,
        "coverImage": details_en.get("header_image", ""),
        "cinematicTrailer": trailer_url,
        "downloadLink": f"https://store.steampowered.com/app/{details_en['steam_appid']}/",
        "rating": round(metacritic / 10, 1) if metacritic is not None else (review_rating or 8.0),
        "categories": categories,
        "releaseDate": release_date,
        "updatedAt": now,
        "createdAt": now,
    }


def find_manual_game_data(title: str | None) -> dict | None:
    if not title:
        return None

    if title in MANUAL_GAME_DATA:
        return MANUAL_GAME_DATA[title]

    for entry in MANUAL_GAME_DATA.values():
        if title in entry["titleI18n"].values():
            return entry

    return None


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
            details_en = fetch_app_details(app_id, "english")
            details_zh = fetch_app_details(app_id, "schinese")
            document = build_game_document(details_en, details_zh)
        except Exception as exc:
            skipped.append((app_id, f"fetch_failed: {exc}"))
            continue

        created_at = document.pop("createdAt")
        result = collection.update_one(
            {"downloadLink": document["downloadLink"]},
            {
                "$set": document,
                "$unset": {"title": "", "description": "", "category": ""},
                "$setOnInsert": {"createdAt": created_at},
            },
            upsert=True,
        )

        english_title = document["titleI18n"].get("en-US", f"Steam {app_id}")
        if result.upserted_id is not None:
            inserted.append(english_title)
        elif result.modified_count > 0:
            updated.append(english_title)
        else:
            skipped.append((app_id, f"unchanged: {english_title}"))

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
