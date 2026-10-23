#!/usr/bin/env python3
"""Import Discord's language system into Lampcord.

Discord's Android app (as dumped by jadx at ``app/``) ships ~10k strings in
``app/src/main/res/values*/strings.xml`` across ~170 locale directories. Lampcord
is a Compose Multiplatform app that loads localized strings through the Compose
Resources library (``composeResources/values*/strings.xml``).

This script bridges the two:

  * works out which strings are actually "app strings" by looking at every
    ``R.string.*`` / ``R.plurals.*`` reference in Discord's decompiled code and
    every ``@string/*`` / ``@plurals/*`` reference in its XML resources,
  * keeps only the 30 locales Discord exposes in its language picker,
  * rewrites Android-only escape sequences (``\\'``, ``\\"``, ``\\@``, ``\\?``)
    that Compose Resources does not understand,
  * and emits Compose Resources directories.

Usage::

    python3 scripts/import_discord_strings.py \
        --discord /home/lampu/discord-jadx/app \
        --out shared/src/commonMain/composeResources
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# Discord locale folder -> (Compose qualifier, BCP-47 tag, English label, native label)
# Order matters: first entry is the default (values/) locale.
LOCALES: list[tuple[str, str, str, str]] = [
    ("values", None, "en-US", "English, USA"),
    ("values-en-rGB", "en-rGB", "en-GB", "English, UK"),
    ("values-bg", "bg", "bg", "български"),
    ("values-cs", "cs", "cs", "Čeština"),
    ("values-da", "da", "da", "Dansk"),
    ("values-de", "de", "de", "Deutsch"),
    ("values-el", "el", "el", "Ελληνικά"),
    ("values-es-rES", "es-rES", "es-ES", "Español"),
    ("values-fi", "fi", "fi", "Suomi"),
    ("values-fr", "fr", "fr", "Français"),
    ("values-hi", "hi", "hi", "हिंदी"),
    ("values-hr", "hr", "hr", "Hrvatski"),
    ("values-hu", "hu", "hu", "Magyar"),
    ("values-it", "it", "it", "Italiano"),
    ("values-ja", "ja", "ja", "日本語"),
    ("values-ko", "ko", "ko", "한국어"),
    ("values-lt", "lt", "lt", "Lietuviškai"),
    ("values-nl", "nl", "nl", "Nederlands"),
    ("values-no", "no", "no", "Norsk"),
    ("values-pl", "pl", "pl", "Polski"),
    ("values-pt-rBR", "pt-rBR", "pt-BR", "Português do Brasil"),
    ("values-ro", "ro", "ro", "Română"),
    ("values-ru", "ru", "ru", "Русский"),
    ("values-sv-rSE", "sv-rSE", "sv-SE", "Svenska"),
    ("values-th", "th", "th", "ไทย"),
    ("values-tr", "tr", "tr", "Türkçe"),
    ("values-uk", "uk", "uk", "Українська"),
    ("values-vi", "vi", "vi", "Tiếng Việt"),
    ("values-zh-rCN", "zh-rCN", "zh-CN", "中文"),
    ("values-zh-rTW", "zh-rTW", "zh-TW", "繁體中文"),
]

# Strings that only exist in Lampcord (Discord has no equivalent). Written to
# ``values/lampcord.xml`` in the default locale only; every other locale falls
# back to English through Compose Resources' normal fallback. Keys are prefixed
# with ``lampcord_`` so they never collide with imported Discord keys.
LAMPCORD_EXTRA_STRINGS: dict[str, str] = {
    # Settings > Language
    "lampcord_language_system": "System",
    "lampcord_language_system_description": "Use your device language",
    "lampcord_language_description": "Select your preferred language",
    # Login
    "lampcord_login_token_label": "Discord Token",
    "lampcord_login_token_hide": "Hide token",
    "lampcord_login_token_show": "Show token",
    "lampcord_login_password_hide": "Hide password",
    "lampcord_login_password_show": "Show password",
    "lampcord_login_with_token": "Login with token",
    "lampcord_login_with_credentials": "Use email and password instead",
    "lampcord_login_mfa_title": "Enter 2FA Code",
    "lampcord_login_backup_title": "Enter Backup Code",
    "lampcord_login_mfa_code_label": "6-digit code",
    "lampcord_login_backup_code_label": "8-digit backup code",
    "lampcord_login_use_backup": "Use Backup Code",
    "lampcord_login_use_authenticator": "Use Authenticator App",
    "lampcord_login_qr_prompt": "Or scan this QR code to log in instantly",
    "lampcord_login_qr_approve": "Approve on your phone",
    "lampcord_login_error_enter_token": "Enter a token",
    "lampcord_login_error_failed": "Login failed (check your connection)",
    "lampcord_login_error_invalid": "Invalid login or password",
    "lampcord_login_error_invalid_mfa": "Invalid code or expired ticket",
    "lampcord_login_error_unexpected": "An unexpected error occurred",
    # Settings > Accessibility
    "lampcord_settings_visual": "Visual",
    "lampcord_settings_saturation": "Saturation",
    "lampcord_settings_motion": "Motion",
    "lampcord_settings_reduced_motion_desc": "Reduces interface motion and prevents GIFs, animated emojis, stickers, and avatars from playing automatically.",
    "lampcord_settings_detection": "Detection",
    "lampcord_settings_allow_accessibility_detection": "Allow Accessibility Detection",
    "lampcord_settings_allow_accessibility_detection_desc": "Allow Discord to detect if you are using accessibility tools.",
    # Settings > Advanced
    "lampcord_settings_advanced": "Advanced",
    "lampcord_settings_developer_settings": "Developer Settings",
    "lampcord_settings_developer_mode_desc": "Exposes ID copying and other advanced debug tools.",
    "lampcord_settings_show_hidden_channels": "Show Hidden Channels",
    "lampcord_settings_show_hidden_channels_desc": "Display channels you don't have permission to view as locked and greyed out.",
    "lampcord_settings_window": "Window",
    "lampcord_settings_use_custom_titlebar": "Use Custom Titlebar",
    "lampcord_settings_use_custom_titlebar_desc": "Use a custom in-app title bar instead of the system window frame. Requires restart.",
    "lampcord_settings_wayland_scaling": "Enable Wayland Scaling Fix",
    "lampcord_settings_wayland_scaling_desc": "Enables automatic display scaling detection on Wayland compositors. Requires restart.",
    "lampcord_settings_data_management": "Data Management",
    "lampcord_settings_clear_cache": "Clear Cache",
    "lampcord_settings_clear_cache_desc": "Delete all locally cached images and messages.",
    "lampcord_settings_reset_app_state": "Reset App State",
    "lampcord_settings_reset_app_state_desc": "Restore all settings to their default values.",
    # Settings > Navigation
    "lampcord_settings_navigation": "Navigation",
    "lampcord_settings_nav_home": "Home",
    "lampcord_settings_nav_you": "You",
    "lampcord_settings_tabs_visibility": "Tabs Visibility",
    "lampcord_settings_hide_nav_labels": "Hide Navigation Labels",
    "lampcord_settings_hide_nav_labels_desc": "Only show icons in the navigation bar.",
    "lampcord_settings_tab_order": "Tab Order",
    # Settings > Connections
    "lampcord_settings_no_connections": "No connected accounts found",
    "lampcord_settings_add_connection": "Add Connection",
    # Settings > Account
    "lampcord_settings_password_auth": "Password and Authentication",
    "lampcord_settings_two_fa_enabled": "Two-Factor Authentication: Enabled",
    "lampcord_settings_two_fa_disabled": "Two-Factor Authentication: Disabled",
    # Settings > Notifications
    "lampcord_settings_enable_notifications": "Enable Notifications",
    "lampcord_settings_enable_notifications_desc": "Show notifications in your system tray when Lampcord is in the background.",
    "lampcord_settings_show_message_preview": "Show Message Preview",
    "lampcord_settings_show_message_preview_desc": "Include message content in notifications.",
    "lampcord_settings_message_sound": "Message Sound",
    "lampcord_settings_message_sound_desc": "Play a sound when you receive a message.",
    "lampcord_settings_incoming_call_sound": "Incoming Call Sound",
    "lampcord_settings_incoming_call_sound_desc": "Play a sound when you are being called.",
    "lampcord_settings_show_in_app_notifications": "Show In-App Notifications",
    "lampcord_settings_show_in_app_notifications_desc": "Display banners in the app when someone mentions or pings you.",
    # Settings > Privacy & Safety
    "lampcord_settings_content_filter": "Content Filter",
    "lampcord_settings_content_filter_edge": "I live on the edge",
    "lampcord_settings_content_filter_off_help": "Don't scan any direct messages.",
    "lampcord_settings_dm_server_members_desc": "This setting is applied when you join a new server.",
    "lampcord_settings_friend_requests": "Friend Requests",
    "lampcord_settings_content_controls": "Content Controls",
    "lampcord_settings_hide_blocked_messages": "Hide Blocked Messages",
    "lampcord_settings_hide_blocked_messages_desc": "Completely remove messages from blocked users.",
    # Settings > Updates
    "lampcord_settings_check_for_updates": "Check for Updates",
    "lampcord_settings_check_for_updates_desc": "Look for a newer build when Lampcord opens.",
    "lampcord_settings_update_notifications": "Update Notifications",
    "lampcord_settings_update_notifications_desc": "Tell me when an update is ready to install.",
    "lampcord_settings_check_now": "Check for updates",
    "lampcord_settings_view_changelog": "View changelog",
    "lampcord_settings_channel_nightly": "Channel: Nightly",
    "lampcord_settings_channel_stable": "Channel: Stable",
    "lampcord_settings_nightly_desc": "Nightly builds come from the latest commit to main and can be unstable.",
    "lampcord_settings_checking": "Checking for updates",
    "lampcord_settings_up_to_date": "Lampcord is up to date",
    "lampcord_settings_version_fmt": "Version %1$s",
    "lampcord_settings_available_fmt": "Version %1$s is available",
    "lampcord_settings_downloading_fmt": "Downloading %1$s",
    "lampcord_settings_ready_fmt": "%1$s is ready to install",
    "lampcord_settings_install_restart": "Install and restart",
    # Settings > Changelog
    "lampcord_settings_no_release_notes": "No release notes were published.",
    # Settings > Debug log
    "lampcord_settings_debug_logs": "Debug Logs",
    "lampcord_settings_search_logs": "Search logs...",
    "lampcord_settings_auto_scroll": "Auto-scroll",
    "lampcord_settings_all": "All",
    "lampcord_settings_all_tags": "All Tags",
    "lampcord_settings_refresh": "Refresh",
    "lampcord_settings_clear": "Clear",
}

# Names that look like app strings but are library/framework plumbing we never
# want to expose to the UI layer.
LIBRARY_PREFIXES = (
    "abc_",
    "androidx_",
    "android_",
    "mtrl_",
    "material_",
    "common_",
    "com_",
    "play_",
    "gcm_",
    "google_",
    "grow_",
    "preference_",
    "clock_",
    "cast_",
)

STRING_REF_RE = re.compile(r"R\.string\.([A-Za-z0-9_]+)(?![A-Za-z0-9_…])")
PLURAL_REF_RE = re.compile(r"R\.plurals\.([A-Za-z0-9_]+)(?![A-Za-z0-9_…])")
XML_STRING_REF_RE = re.compile(r"@string/([A-Za-z0-9_]+)")
XML_PLURAL_REF_RE = re.compile(r"@plurals/([A-Za-z0-9_]+)")

# <string name="foo" ...>inner</string>  (inner may span lines)
STRING_ENTRY_RE = re.compile(
    r"<string\b[^>]*\bname=\"([^\"]+)\"[^>]*>(.*?)</string>", re.DOTALL
)
STRING_SELF_CLOSING_RE = re.compile(r"<string\b[^>]*\bname=\"([^\"]+)\"[^>]*/>")
PLURAL_ENTRY_RE = re.compile(
    r"<plurals\b[^>]*\bname=\"([^\"]+)\"[^>]*>(.*?)</plurals>", re.DOTALL
)

# Android-only escapes that Compose Resources does not unescape. ``\n``, ``\t``,
# ``\uXXXX`` and ``\\`` are handled natively by Compose, so we leave them alone.
QUOTE_ESCAPES = {"'", '"', "@", "?"}


def android_unescape(value: str) -> str:
    """Drop Android-only backslash escapes Compose Resources does not support."""
    out: list[str] = []
    i = 0
    length = len(value)
    while i < length:
        char = value[i]
        if char == "\\" and i + 1 < length and value[i + 1] in QUOTE_ESCAPES:
            out.append(value[i + 1])
            i += 2
            continue
        out.append(char)
        i += 1
    return "".join(out)


def parse_string_entries(text: str) -> dict[str, str]:
    entries: dict[str, str] = {}
    # Drop self-closing tags first: otherwise the entry regex swallows the
    # following sibling's opening tag as this string's body.
    for name in STRING_SELF_CLOSING_RE.findall(text):
        entries.setdefault(name, "")
    text = STRING_SELF_CLOSING_RE.sub("", text)
    for name, body in STRING_ENTRY_RE.findall(text):
        entries.setdefault(name, body)
    return entries


def parse_plural_entries(text: str) -> dict[str, str]:
    return {name: body for name, body in PLURAL_ENTRY_RE.findall(text)}


def collect_referenced_keys(discord: Path) -> tuple[set[str], set[str]]:
    strings: set[str] = set()
    plurals: set[str] = set()

    java_root = discord / "src" / "main" / "java"
    if java_root.is_dir():
        for path in java_root.rglob("*.java"):
            text = path.read_text(encoding="utf-8", errors="ignore")
            strings.update(STRING_REF_RE.findall(text))
            plurals.update(PLURAL_REF_RE.findall(text))

    res_root = discord / "src" / "main" / "res"
    if res_root.is_dir():
        for path in res_root.rglob("*.xml"):
            text = path.read_text(encoding="utf-8", errors="ignore")
            strings.update(XML_STRING_REF_RE.findall(text))
            plurals.update(XML_PLURAL_REF_RE.findall(text))

    return strings, plurals


def is_library_name(name: str) -> bool:
    return name.startswith(LIBRARY_PREFIXES)


def escape_text(value: str) -> str:
    # The source is already valid XML, so entities and markup are preserved
    # verbatim. Only Android-specific backslash escapes need rewriting.
    return android_unescape(value)


def write_strings_xml(
    path: Path, entries: dict[str, str], plural_entries: dict[str, str]
) -> None:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for name in sorted(entries):
        lines.append(f'    <string name="{name}">{entries[name]}</string>')
    lines.append("</resources>")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    if plural_entries:
        plines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
        for name in sorted(plural_entries):
            plines.append(f'    <plurals name="{name}">')
            body = plural_entries[name]
            for item in re.findall(r"<item\b.*?</item>", body, re.DOTALL):
                plines.append("        " + item.strip())
            plines.append("    </plurals>")
        plines.append("</resources>")
        path.with_name("plurals.xml").write_text(
            "\n".join(plines) + "\n", encoding="utf-8"
        )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--discord",
        type=Path,
        default=Path("/home/lampu/discord-jadx/app"),
        help="Path to Discord's decompiled app/ directory.",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("shared/src/commonMain/composeResources"),
        help="Destination composeResources directory.",
    )
    parser.add_argument(
        "--include-library-strings",
        action="store_true",
        help="Keep framework/library strings (abc_, androidx_, ...).",
    )
    args = parser.parse_args()

    res_root = args.discord / "src" / "main" / "res"
    if not res_root.is_dir():
        print(f"error: {res_root} does not exist", file=sys.stderr)
        return 1

    base_strings = parse_string_entries((res_root / "values" / "strings.xml").read_text(encoding="utf-8"))
    base_plurals = parse_plural_entries((res_root / "values" / "plurals.xml").read_text(encoding="utf-8"))

    ref_strings, ref_plurals = collect_referenced_keys(args.discord)

    app_string_keys = {k for k in ref_strings if k in base_strings}
    app_plural_keys = {k for k in ref_plurals if k in base_plurals}
    if not args.include_library_strings:
        app_string_keys = {k for k in app_string_keys if not is_library_name(k)}
        app_plural_keys = {k for k in app_plural_keys if not is_library_name(k)}

    print(
        f"referenced: {len(ref_strings)} strings / {len(ref_plurals)} plurals; "
        f"kept: {len(app_string_keys)} strings / {len(app_plural_keys)} plurals"
    )

    written = 0

    # Lampcord-specific strings (no Discord equivalent) go in the default
    # locale only; other locales fall back to English automatically.
    lampcord_values = args.out / "values"
    lampcord_values.mkdir(parents=True, exist_ok=True)
    write_strings_xml(
        lampcord_values / "lampcord.xml", dict(LAMPCORD_EXTRA_STRINGS), {}
    )

    for discord_dir, compose_qualifier, _tag, _label in LOCALES:
        source_dir = res_root / discord_dir
        strings_file = source_dir / "strings.xml"
        if not strings_file.is_file():
            print(f"  skip {discord_dir}: missing strings.xml", file=sys.stderr)
            continue

        entries = parse_string_entries(strings_file.read_text(encoding="utf-8"))
        filtered = {
            k: escape_text(entries[k]) for k in app_string_keys if k in entries
        }

        plural_file = source_dir / "plurals.xml"
        plural_entries = parse_plural_entries(plural_file.read_text(encoding="utf-8")) if plural_file.is_file() else {}
        filtered_plurals = {
            k: escape_text(plural_entries[k]) for k in app_plural_keys if k in plural_entries
        }

        out_dir = args.out / (
            "values" if compose_qualifier is None else f"values-{compose_qualifier}"
        )
        out_dir.mkdir(parents=True, exist_ok=True)
        write_strings_xml(out_dir / "strings.xml", filtered, filtered_plurals)
        written += 1
        print(f"  {discord_dir:16} -> {out_dir.relative_to(args.out)} ({len(filtered)} strings, {len(filtered_plurals)} plurals)")

    print(f"wrote {written} locales into {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
