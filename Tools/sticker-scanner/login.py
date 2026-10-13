"""
Run this ONCE, interactively, to log in to Telegram and print a session string.

Why this exists: scan_official_stickers.py needs to call Telegram's
messages.getFeaturedStickers / messages.getOldFeaturedStickers, which are only
available to a logged-in user account (not a bot). Telethon's "StringSession" lets
that login happen once here, with you typing your phone number and the login code
Telegram sends you - and then the resulting session string gets saved as a GitHub
secret, so the scheduled workflow can reuse it without ever needing to log in again
or store your phone number anywhere.

Usage (in this Codespace's terminal):
    cd Tools/sticker-scanner
    pip install -r requirements.txt
    python3 login.py
    # follow the prompts (phone number, then the code Telegram sends you)
    # it will print a long string starting with "1BVtsO..." - copy that whole line

Then add it as a repository secret named TELEGRAM_SESSION_STRING
(the same API_ID / API_HASH secrets the Android app already uses are reused here -
no new Telegram app registration needed).

Security note: this session string is equivalent to being logged into your Telegram
account from a new device. Treat it like a password - only paste it into the GitHub
secret field, never into a file that gets committed. Consider using a secondary
Telegram account for this rather than your main one, since this runs unattended on a
schedule from GitHub's servers.
"""
from telethon.sync import TelegramClient
from telethon.sessions import StringSession

API_ID = input("API ID (same apiId used for the Android app build): ").strip()
API_HASH = input("API hash (same apiHash used for the Android app build): ").strip()

with TelegramClient(StringSession(), int(API_ID), API_HASH) as client:
    session_string = client.session.save()
    print("\n" + "=" * 60)
    print("Login successful. Save this as the TELEGRAM_SESSION_STRING secret:")
    print("=" * 60)
    print(session_string)
    print("=" * 60)
