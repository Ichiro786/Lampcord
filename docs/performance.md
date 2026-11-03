# Performance notes

These are the performance rules the app follows, plus the known remaining work.

## Dispatchers

Shared stores run on `Dispatchers.Default`. UI-only collectors stay on `Dispatchers.Main`.
Gateway events are handled off the main thread. Read acknowledgements are debounced before
the REST request and the local preference write.

## Message list

- Unread count and first unread are derived state, not recomputed on every emission.
- Blocked authors are a `Set<String>`, built once per relationship change.
- Rows do not use `zIndex`, per-row `graphicsLayer`, or `animateItem()`.
- Message body scans are remembered by content and embed data.

## Images

- Coil has a 256 MB disk cache under the app storage directory and a 32 MB memory cache.
- Requests do not override Coil's cache key, so size and data are part of the key.
- Small images skip the crossfade.
- Remaining: size-bucketed CDN URLs, blackhole prefetch for the next page, and moving
  twemoji resource reads off the composition thread.

## State lookup

- `UserStore` exposes `memberState(guildId, userId)` for per-row member observation.
- `PresenceStore` exposes `presenceState(userId)` for per-row presence observation.
- Remaining: per-channel `MessageStore` state and per-key channel/read-state lookup.

## Database

The message log has a composite `(channelId, timestamp)` index and a 90-day retention pass.
Queries and JSON decoding run on IO or Default.

Remaining: transactional batch inserts, offline message cache, and a storage settings page.

## Desktop

The window backend is Nucleus 3.0 with system decorations. Hyprland uses `undecorated = true`.
Window size and placement are persisted. Remaining: native window cache trimming.

## Next

These are the larger changes that need their own PRs:

1. Per-channel `MessageStore` state, so a message in one channel never rebuilds every
   cached channel.
2. Per-key channel and read-state lookup instead of collecting whole maps in channel rows.
3. Size-bucketed CDN URLs and blackhole prefetch for the next page of avatars and attachments.
4. Offline message cache served from the database with a stale-data badge.
5. Registry-driven settings with deep search that scrolls to and highlights the matching row.
6. A storage page with image and database usage, plus a confirmation flow for reset.
7. A typed error and empty-state kit with retry actions.
8. Live tray state, close-to-tray, and a desktop shortcut layer.
9. `commonTest` plus CI gates, ktlint, Android lint, and split verification from packaging.
10. A Macrobenchmark module for startup and message-list scroll.
