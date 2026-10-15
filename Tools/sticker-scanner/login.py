"""
Run this ONCE, interactively, to log in to Telegram and save the resulting session
string straight to GitHub as a repo secret - no manual copy/paste needed.

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

If this is run inside a GitHub Codespace (or anywhere the `gh` CLI is already
installed and signed in - which Codespaces is, by default), the script saves the
session string directly as the repo secret TELEGRAM_SESSION_STRING by running
`gh secret set` for you, piping the value in rather than ever typing/pasting it
anywhere - nothing to copy. If `gh` isn't available or isn't signed in, it falls
back to just printing the string so you can add it manually (Settings -> Secrets
and variables -> Actions -> New repository secret).

(The same API_ID / API_HASH secrets the Android app already uses are reused here -
no new Telegram app registration needed.)

Security note: this session string is equivalent to being logged into your Telegram
account from a new device. Consider using a secondary Telegram account for this
rather than your main one, since this runs unattended on a schedule from GitHub's
servers.
"""
import shutil
import subprocess

from telethon.sync import TelegramClient
from telethon.sessions import StringSession

SECRET_NAME = "TELEGRAM_SESSION_STRING"


def save_secret_via_gh(session_string):
    """Tries to set the GitHub secret directly via the gh CLI, piping the value in on
    stdin so it never appears as a command-line argument, gets logged, or needs to be
    copy/pasted by hand. Returns True on success, False if gh isn't usable here (not
    installed, not signed in, no repo detected, etc.) - in which case the caller falls
    back to printing the string for manual entry."""
    if shutil.which("gh") is None:
        return False
    try:
        result = subprocess.run(
            ["gh", "secret", "set", SECRET_NAME],
            input=session_string,
            text=True,
            capture_output=True,
        )
    except OSError:
        return False
    if result.returncode != 0:
        print(f"(gh secret set failed: {result.stderr.strip()})")
        return False
    return True


API_ID = input("API ID (same apiId used for the Android app build): ").strip()
API_HASH = input("API hash (same apiHash used for the Android app build): ").strip()

def login_with_code(client):
    """Normal login: Telegram sends a code - NOTE it usually arrives INSIDE the Telegram
    app (chat named "Telegram", with the 5-digit code) on any device already logged in to
    that account, not as an SMS."""
    client.start()  # prompts for phone, code, and 2FA password if needed


def login_with_link(client):
    """Fallback when no code ever arrives: log in by opening a tg://login link in the
    Telegram app on a device that's already logged in (the same mechanism as
    Settings -> Devices -> Link Desktop Device, but via link instead of scanning a QR)."""
    from getpass import getpass
    from telethon.errors import SessionPasswordNeededError

    client.connect()
    qr = client.qr_login()
    while True:
        print("\nOpen this link in the Telegram app (tap it, or paste it into any chat")
        print("and tap it there), then confirm the login:\n")
        print(qr.url)
        try:
            qr.wait(timeout=60)
            break
        except SessionPasswordNeededError:
            client.sign_in(password=getpass("2FA password: "))
            break
        except TimeoutError:
            print("\n(link expired, generating a new one...)")
            qr.recreate()


print("\nHow do you want to log in?")
print("  1) Phone number + code (default)")
print("  2) Link (use this if Telegram never sends you a code)")
method = input("Choose 1 or 2: ").strip() or "1"

client = TelegramClient(StringSession(), int(API_ID), API_HASH)
try:
    if method == "2":
        login_with_link(client)
    else:
        login_with_code(client)
    if not client.is_user_authorized():
        raise SystemExit("Login did not complete - nothing was saved.")
    session_string = client.session.save()
finally:
    client.disconnect()

print("\nLogin successful. Saving it as the repo secret via gh...")
if save_secret_via_gh(session_string):
    print(f"Done - {SECRET_NAME} is now set on the repo. No copy/paste needed.")
else:
    print(
        "\nCouldn't set it automatically (gh not available/signed in here), so "
        "you'll need to add it by hand this one time:"
    )
    print("=" * 60)
    print(session_string)
    print("=" * 60)
    print(
        f"Go to the repo's Settings -> Secrets and variables -> Actions, and add a "
        f"new secret named {SECRET_NAME} with the value above."
    )
