# .NET Interview Hub

An offline-first Android application prepared for Ashutosh Parmar's Senior .NET interview revision. The initial database contains all 29 documents from the interview-preparation set.

## What the app provides

- All 29 interview documents available offline after installation.
- Full-text search across document titles and content.
- Topic-category filtering and bookmark-only filtering.
- Readable document view with heading formatting and selectable text.
- Bookmarking for quick revision.
- Import of `.docx`, `.txt`, and `.md` documents from the Android file picker.
- Create a note directly in the app.
- Edit any document's title, category, or content.
- Replace an existing topic from a modified DOCX/TXT/MD while preserving its title and category.
- Delete a topic from the app without deleting the original external file.
- Local SQLite storage; no account, server, or internet connection is required.

## Initial document set

The app seeds its database from `app/src/main/assets/seed_documents.json` on first launch. This asset is generated from the 16 earlier topic documents, 12 advanced guides, and the spoken-answer script.

## Open and build

1. Open this `DotNetInterviewHub` folder in Android Studio.
2. Allow Gradle sync to finish.
3. Select an Android device or emulator running Android 7.0 or newer.
4. Run the `app` configuration.
5. To create an installable APK, use **Build → Build APK(s)**.

The project uses only Android framework APIs. There are no third-party runtime libraries.

## Build without Android Studio

The project includes `.github/workflows/build-apk.yml`. After the project contents are uploaded to a GitHub repository, GitHub Actions installs the required Java, Android SDK and Gradle versions, builds the debug APK, and makes it available as a downloadable workflow artifact.

See [GITHUB_BUILD_GUIDE.md](GITHUB_BUILD_GUIDE.md) for browser-only instructions.

## Maintaining content through the app

### Add a new document

Tap **Import DOCX / TXT**, choose a supported file, then review its title and category in the editor. The imported text is stored in the app database.

### Replace a modified document

Open the topic, scroll to **Maintain this topic**, and tap **Replace document**. Choose the modified file. The app replaces the content while preserving the existing title and category. Use **Edit** afterward if those fields also need to change.

### Edit content directly

Open a topic and tap **Edit**. Use `#`, `##`, or `###` at the start of a line for formatted headings.

## Data behaviour

The seeded documents are copied into the local database only during the first installation. Later edits and replacements are not overwritten by an app restart. Clearing app data or uninstalling the app removes the locally maintained content.

## Supported import formats

- DOCX: text, headings, basic list markers, and table-cell text are extracted.
- TXT and Markdown: UTF-8 text is imported directly.
- Legacy `.doc` and PDF are intentionally not accepted because reliable offline text extraction would require additional conversion libraries.

## Future enhancements

The SQLite repository and import workflow are separated from the screens, making it straightforward to add database export/import, cloud synchronization, reading progress, quizzes, or PDF support in a later release.
