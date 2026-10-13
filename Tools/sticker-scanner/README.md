# Official sticker auto-scanner

Automatically finds Telegram's official sticker packs and adds any new ones to our
backend database, on a schedule, with no manual work after the one-time setup below.

## What this actually does (please read)

- Telegram does **not** provide an API that lists "every official sticker pack that
  exists." The closest real thing is Telegram's own **featured stickers** list - packs
  Telegram's team curates and updates over time. This scanner reads that list (plus its
  older backlog) and keeps only the ones Telegram marks as `official`. When Telegram adds
  a new one to that list, this scanner will pick it up on its next scheduled run.
- Telegram's API has **no category field** for sticker packs, so every pack this
  scanner adds gets `category: "Custom"` - same as you described (fill in a category if
  one exists, otherwise default to Custom; there's simply never one available here).
- No new database field was added. Auto-added packs reuse the existing
  `owner_telegram_id` field, set to the fixed text `"telegram_official"`, so you can
  always tell them apart from user-submitted packs - exactly as you confirmed that field
  doesn't have to hold a real numeric Telegram ID.

## One-time setup

1. In this Codespace's terminal:
   ```
   cd Tools/sticker-scanner
   pip install -r requirements.txt
   python3 login.py
   ```
2. It will ask for your **API ID** and **API hash** - these are the same `API_ID` /
   `API_HASH` values already saved as repo secrets for the Android app build. (Settings →
   Secrets and variables → Actions, to view the names, though the values are hidden -
   just re-enter the same ones you used before.)
3. It will then ask for a phone number and the login code Telegram sends you. This logs
   in once, right here, and never needs to happen again.
4. It prints a long string. Copy that whole string.
5. Go to the repo's **Settings → Secrets and variables → Actions**, and add a new
   secret named `TELEGRAM_SESSION_STRING` with that string as the value.

**Security note:** that string is equivalent to being logged into a Telegram account
from a new device - treat it like a password, and never paste it anywhere except that
GitHub secret field. Since this will run unattended on a schedule, it's safer to use a
secondary/throwaway Telegram account for this login rather than your main personal
account - if that account ever needs to be logged out, you can do that from Telegram's
own Settings → Devices on that account.

## Running it

- It runs automatically on a schedule once `.github/workflows/sticker-scan.yml` is in
  place (no action needed from you).
- To run it manually any time: go to the repo's **Actions** tab → **Sticker Scanner** →
  **Run workflow**.
- Each run prints a summary (how many official packs were found, how many were new, how
  many were added) in the workflow's log.
