# Knowledge Hub for .NET and ChangeGuard

An offline-first Android application for Ashutosh Parmar's Senior .NET interview revision, ChangeGuard engineering documentation, and future personal knowledge. The initial database contains all 29 interview-preparation documents.

## What the app provides

- All 29 interview documents available offline after installation.
- Separate **Interview Preparation** and **ChangeGuard** workspaces, with support for additional workspaces.
- Fast full-text search across document titles, tags, and complete content.
- Workspace, category, tag, bookmark, and sorting filters.
- Folders and comma-separated tags maintained from the document editor.
- Automatic version history before every edit or file replacement, with one-tap restore.
- Reading progress and recently-opened sorting.
- Complete JSON backup and restore for documents, workspaces, bookmarks, progress, and history.
- Readable document view with heading formatting and selectable text.
- Offline formatted DOCX preview preserving headings, colours, emphasis, shaded blocks, hyperlinks, numbering, images, page breaks, headers/footers, and merged tables.
- Safe background imports with progress feedback, a 20 MB file limit, expanded-size limits, and hardened XML processing.
- Clear Formatted, Text, and Audio reading modes for imported Word documents.
- Built-in read-aloud controls using the phone's English text-to-speech voice.
- Pause/resume, restart, stop, adjustable speed, and automatic long-document sectioning.
- Bookmarking for quick revision.
- Import of `.docx`, `.txt`, and `.md` documents from the Android file picker.
- Create a note directly in the app.
- Edit any document's title, category, or content.
- Replace an existing topic from a modified DOCX/TXT/MD while preserving its title and category.
- Delete a topic from the app without deleting the original external file.
- Versioned local SQLite storage with full-text indexing; no account, server, or internet connection is required.

## Initial document set

The app seeds its database from `app/src/main/assets/seed_documents.json` on first launch. This asset is generated from the 16 earlier topic documents, 12 advanced guides, and the spoken-answer script.

## Open and build

1. Open this `DotNetInterviewHub` folder in Android Studio.
2. Allow Gradle sync to finish.
3. Select an Android device or emulator running Android 7.0 or newer.
4. Run the `app` configuration.
5. To create an installable APK, use **Build → Build APK(s)**.

The project uses only Android framework APIs. There are no third-party runtime libraries.

Development APKs use the bundled `personal-debug.keystore` so APKs produced by later GitHub builds keep a consistent signature and can be installed as updates. This development key is for personal installation only and must not be used to publish a production app to Google Play.

## Build without Android Studio

The project includes `.github/workflows/build-apk.yml`. After the project contents are uploaded to a GitHub repository, GitHub Actions installs the required Java, Android SDK and Gradle versions, builds the debug APK, and makes it available as a downloadable workflow artifact.

See [GITHUB_BUILD_GUIDE.md](GITHUB_BUILD_GUIDE.md) for browser-only instructions.

## Maintaining content through the app

### Add a new document

Choose the correct workspace, tap **Import**, choose a supported file, then review its title, category, folder, and tags in the editor.

### Replace a modified document

Open the document, scroll to **Maintain this document**, and tap **Replace**. The previous content is saved automatically. Tap **History** to restore any earlier version.

### Edit content directly

Open a topic and tap **Edit**. Use `#`, `##`, or `###` at the start of a line for formatted headings.

### Back up your library

Tap **Manage → Export backup** and save the JSON file in Google Drive or another safe location. Use **Restore backup** on a new installation or phone. Restoring replaces the current local library, so export it first when necessary.

## Data behaviour

The seeded documents are copied into the local database only during the first installation. Updating to Version 2.2 preserves existing documents, edits, bookmarks, and history. Clearing app data or uninstalling still removes locally maintained content, so export a backup first. Replace a previously imported DOCX once if you want its stored preview regenerated with the newest Word compatibility features.

## Supported import formats

- DOCX: searchable/read-aloud text is extracted, and a separate offline formatted preview preserves the document's main visual structure.
- TXT and Markdown: UTF-8 text is imported directly.
- Legacy `.doc` and PDF are intentionally not accepted because reliable offline text extraction would require additional conversion libraries.

After importing or replacing a DOCX, open the document and select **Formatted**, **Text**, or **Audio**. The normal text content remains available for editing, search, and read-aloud.

## Architecture

The app remains dependency-free and inexpensive to build. SQL access is centralized in `DocumentRepository`, speech state is isolated in `SpeechController`, and DOCX parsing is isolated in `DocumentImport`, `DocxSecurity`, `DocxTextExtractor`, and `DocxHtmlExtractor`. Schema version 3 stores format-preserving HTML without changing searchable text. GitHub Actions validates DOCX security and compatibility before linting and building the APK.
