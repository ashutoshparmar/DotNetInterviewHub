# Change log

## 2.2.0 — Safe Word compatibility

- Moved DOCX, TXT, and Markdown import/replacement work off the Android UI thread.
- Added progress feedback while a document is checked, extracted, and formatted.
- Added input, internal ZIP entry, and expanded-content size limits.
- Hardened XML processing against DTD, external entity, and external resource access.
- Added automatic Word numbering, embedded images, page breaks, headers, footers, and merged table cells.
- Added clear Formatted, Text, and Audio reading modes.
- Locked down network and active-content features in the formatted viewer.
- Added generated compatibility/security validation and Android lint to GitHub Actions.
- Updated the app to version 2.2.0 (version code 6); the database remains schema version 3.

## 2.1.0 — Formatted DOCX view

- Added an offline formatted DOCX view using Android WebView.
- Preserved DOCX headings, run emphasis, colours, hyperlinks, shaded callouts/code blocks, and table structure.
- Kept a separate plain-text representation for search, editing, and text-to-speech.
- Added database schema version 3 and version-history support for rich previews.
- Preserved the rich preview when only metadata changes; editing document text intentionally clears it until the DOCX is replaced.

## 2.0.1 — Audio pause/resume correction

- Fixed **Resume** restarting from the beginning of a large speech section.
- Tracks the exact spoken character range on Android 8.0 and newer.
- Uses short sentence-based speech sections as a close fallback on Android 7.
- Prevents a speech engine's stop callback from being treated as a playback error during pause.

## 2.0.0 — Knowledge Hub foundation

- Expanded the app from interview revision into a multi-workspace personal Knowledge Hub.
- Added default Interview Preparation and ChangeGuard workspaces and support for creating more.
- Added folders, tags, workspace/category/tag filters, and recent/title/update sorting.
- Added SQLite FTS4 indexing for scalable full-document search.
- Added automatic version history and restoration before every edit or replacement.
- Added reading progress and recently opened tracking.
- Added complete JSON backup and restore from the Android file picker.
- Moved text-to-speech state into a dedicated controller and added five reading speeds.
- Added an additive database migration that preserves version 1.x documents and bookmarks.
- Updated the application version to 2.0.0 (version code 3).

## 1.1.0 — Read-aloud update

- Added **Read aloud**, **Pause/Resume**, **Restart**, and **Stop** controls to every document.
- Added automatic chunking so long interview documents continue section by section.
- Removed Markdown heading and bullet markers before speech for more natural reading.
- Prefers an Indian English voice and falls back to US English when needed.
- Added status updates showing the current audio section.
- Added a consistent personal-development signing key for future GitHub APK updates.

## 1.0.0 — Initial release

- Included all 29 offline interview documents.
- Added search, categories, bookmarks, document import, replacement and editing.
