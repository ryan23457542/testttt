"""
Finds Telegram's official sticker packs and adds any that aren't in our backend yet.

IMPORTANT LIMITATION (read this before relying on it): Telegram has no API that lists
"every official sticker pack that exists" - no such catalog exists on their side at
all. The closest real thing is the "featured stickers" list: a set of sticker packs
Telegram's own team curates and periodically updates (messages.getFeaturedStickers),
plus an older/paginated backlog of previously-featured ones
(messages.getOldFeaturedStickers). That's what this script scans. It is a good proxy
for "official stickers, and alert me to new ones" (new official packs do get added to
this list over time), but it is NOT guaranteed to be literally 100% of every sticker
pack Telegram has ever published - there is no such complete list to query in the
first place.

What it does each run:
  1. Logs into Telegram with a pre-created session (see login.py - run that once).
  2. Fetches the current featured sticker sets, then pages through the older featured
     sticker backlog, keeping only sets flagged `official` by Telegram.
  3. Fetches the short_names already in our own backend (paginating the existing
     GET /api/v1/stickers list endpoint) so we never try to add a duplicate.
  4. POSTs any newly-found official short_name to the backend as a new pack, with
     category "Custom" (Telegram's API has no per-pack category/genre field at all,
     so there is nothing real to auto-detect here - every auto-added pack lands in
     "Custom" until/unless a smarter categorizer is built later) and
     owner_telegram_id set to the fixed marker string below, so these entries are
     clearly distinguishable from user-submitted packs without needing a schema change.

Environment variables (see .github/workflows/sticker-scan.yml for how these are
supplied in CI, or export them yourself to run this locally):
  API_ID, API_HASH            - same Telegram app credentials the Android build uses
  TELEGRAM_SESSION_STRING     - from running login.py once
  STICKER_API_BASE_URL        - e.g. https://backend-api-gn09.onrender.com
  STICKER_API_KEY             - optional; sent as "Authorization: Bearer <key>" if the
                                 backend's create-pack endpoint needs it. Leave unset
                                 if it doesn't.
"""
import os
import sys
import time

import requests
from telethon.sync import TelegramClient
from telethon.sessions import StringSession
from telethon.tl.functions.messages import (
    GetFeaturedStickersRequest,
    GetOldFeaturedStickersRequest,
)

OWNER_MARKER = "telegram_official"
DEFAULT_CATEGORY = "Custom"
OLD_FEATURED_PAGE_SIZE = 50
OLD_FEATURED_MAX_PAGES = 200  # safety cap - stop even if Telegram keeps paginating
REQUEST_DELAY_SECONDS = 0.3


def env(name, required=True, default=None):
    value = os.environ.get(name, default)
    if required and not value:
        hint = (
            " - add it under repo Settings -> Secrets and variables -> Actions "
            "(for TELEGRAM_SESSION_STRING, run Tools/sticker-scanner/login.py once)."
            if name == "TELEGRAM_SESSION_STRING" else ""
        )
        print(f"::error::Missing required environment variable {name}{hint}", file=sys.stderr)
        sys.exit(1)
    return value


def fetch_official_short_names(client):
    """Returns a dict of {short_name: title} for every sticker set Telegram currently
    flags as `official`, from both the live featured list and the older featured
    backlog."""
    found = {}

    current = client(GetFeaturedStickersRequest(hash=0))
    for covered in getattr(current, "sets", []):
        stickerset = covered.set
        if stickerset.official and stickerset.short_name:
            found[stickerset.short_name] = stickerset.title

    offset = 0
    for _ in range(OLD_FEATURED_MAX_PAGES):
        time.sleep(REQUEST_DELAY_SECONDS)
        page = client(GetOldFeaturedStickersRequest(offset=offset, limit=OLD_FEATURED_PAGE_SIZE, hash=0))
        sets = getattr(page, "sets", [])
        if not sets:
            break
        for covered in sets:
            stickerset = covered.set
            if stickerset.official and stickerset.short_name:
                found[stickerset.short_name] = stickerset.title
        offset += len(sets)
        if len(sets) < OLD_FEATURED_PAGE_SIZE:
            break  # reached the end of the backlog

    return found


def fetch_existing_short_names(base_url, api_key):
    """Pages through our own GET /api/v1/stickers list endpoint and returns the set of
    short_names already stored, so we never try to insert a duplicate. Parsing mirrors
    the Android client's defensive parsing (StickerStoreApi.java) since the exact
    response shape isn't formally documented."""
    headers = {"Authorization": f"Bearer {api_key}"} if api_key else {}
    existing = set()
    page = 1
    limit = 50
    while True:
        resp = requests.get(
            f"{base_url}/api/v1/stickers",
            params={"page": page, "limit": limit},
            headers=headers,
            timeout=20,
        )
        resp.raise_for_status()
        body = resp.json()

        if isinstance(body, list):
            items = body
        elif isinstance(body, dict):
            items = next(
                (body[key] for key in ("items", "results", "packs", "data", "stickers") if isinstance(body.get(key), list)),
                [],
            )
        else:
            items = []

        if not items:
            break

        for item in items:
            if isinstance(item, dict):
                short_name = item.get("short_name") or item.get("shortName") or item.get("name")
            elif isinstance(item, str):
                short_name = item
            else:
                short_name = None
            if short_name:
                existing.add(short_name)

        if len(items) < limit:
            break
        page += 1
        time.sleep(REQUEST_DELAY_SECONDS)

    return existing


def add_pack(base_url, api_key, short_name):
    headers = {"Authorization": f"Bearer {api_key}"} if api_key else {}
    payload = {
        "short_name": short_name,
        "category": DEFAULT_CATEGORY,
        "owner_telegram_id": OWNER_MARKER,
    }
    resp = requests.post(f"{base_url}/api/v1/stickers", json=payload, headers=headers, timeout=20)
    return resp


def main():
    api_id = env("API_ID")
    api_hash = env("API_HASH")
    session_string = env("TELEGRAM_SESSION_STRING")
    base_url = env("STICKER_API_BASE_URL").rstrip("/")
    api_key = env("STICKER_API_KEY", required=False)

    print("Connecting to Telegram...")
    client = TelegramClient(StringSession(session_string.strip()), int(api_id), api_hash)
    # IMPORTANT: use connect() + is_user_authorized(), NOT `with TelegramClient(...)` /
    # client.start(). start() falls back to asking for a phone number on stdin whenever
    # the session isn't logged in, which crashes with EOFError in CI (no terminal). A
    # session string that is present but invalid/expired/revoked looks exactly like that,
    # so check explicitly and fail with a clear message instead.
    try:
        client.connect()
        if not client.is_user_authorized():
            print(
                "::error::TELEGRAM_SESSION_STRING is set but Telegram says it is not logged in "
                "(expired, revoked, or copied incorrectly). Re-run Tools/sticker-scanner/login.py "
                "to generate a fresh one and update the TELEGRAM_SESSION_STRING secret.",
                file=sys.stderr,
            )
            sys.exit(1)
        print("Fetching Telegram's official sticker sets (featured + old featured)...")
        official = fetch_official_short_names(client)
    finally:
        client.disconnect()
    print(f"Found {len(official)} official sticker sets from Telegram.")

    print("Fetching already-known packs from our own backend...")
    existing = fetch_existing_short_names(base_url, api_key)
    print(f"Backend already has {len(existing)} packs.")

    new_short_names = [name for name in official if name not in existing]
    print(f"{len(new_short_names)} new official pack(s) to add.")

    added, failed = 0, 0
    for short_name in new_short_names:
        resp = add_pack(base_url, api_key, short_name)
        if resp.ok:
            added += 1
            print(f"  added: {short_name}")
        else:
            failed += 1
            print(f"  FAILED ({resp.status_code}): {short_name} - {resp.text[:200]}")
        time.sleep(REQUEST_DELAY_SECONDS)

    print(f"\nDone. Added {added}, failed {failed}, already had {len(existing)}, skipped (duplicates) {len(official) - len(new_short_names)}.")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
