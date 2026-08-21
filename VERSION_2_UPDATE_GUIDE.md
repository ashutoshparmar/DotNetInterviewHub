# Update the GitHub app to Version 2.2

Version 2.2 uses the same application ID, database schema, and personal signing key as Version 2.1. Install the new APK over the existing app so Android retains the current local documents, bookmarks, formatted previews, and history.

## 1. Keep a safety copy

Do not uninstall the current app. Keep the previously downloaded APK until the new build has been tested.

## 2. Upload the project update

1. Extract `KnowledgeHub_Version_2.2.0_Safe_Word_Compatibility.zip` on the computer.
2. Open the existing private GitHub repository.
3. Use **Add file → Upload files**.
4. Drag all contents from inside the extracted project folder into the repository upload area.
5. GitHub should show the new `DocxSecurity.java` and `validation/DocxFixtureFactory.java` files.
6. Enter the commit message `Upgrade safe Word document compatibility`.
7. Commit directly to the `main` branch.

If the browser does not reliably replace the existing files, delete only the repository's old `app/src/main/java/com/ashutosh/dotnetinterviewhub` folder through GitHub and upload the new folder. Do not delete the repository or the signing key.

## 3. Build and download

1. Open **Actions → Build Android APK**.
2. Open the newest workflow run and wait for a green check mark.
3. Download `DotNetInterviewHub-debug-apk` from **Artifacts**.
4. Extract `app-debug.apk`.

## 4. Install safely

1. Open `app-debug.apk` on the phone.
2. Android should show **Update**, not request an uninstall.
3. Tap **Update**.
4. Open the app and verify that the existing documents and bookmarks remain.
5. Replace the React document once with the corrected `_Word_Fixed.docx` file.
6. Open it and select **Formatted**.
7. Verify its headings, tables, colours, bullets, headers/footers, images when present, and 1–10 final revision numbering.
8. Select **Text** and **Audio** to verify the other reading modes.
9. Open **Manage → Export backup** and save the first Version 2.2 backup.

Previously stored formatted previews remain usable, but they are not rewritten automatically. Replace any older DOCX once when you want its preview regenerated with Version 2.2 numbering, image, merged-cell, header/footer, and page-break support.

If Android says the package conflicts or cannot be installed, stop and do not uninstall the old app. Confirm that `app/personal-debug.keystore`, application ID `com.ashutosh.dotnetinterviewhub`, and version code `6` are present in the uploaded project.

## 5. Suggested ChangeGuard document fields

- Workspace: `ChangeGuard`
- Folder examples: `Product Requirements`, `Architecture and ADRs`, `Backend and APIs`, `Angular UI`, `Database`, `Testing`, `Deployment`, or `Daily Notes`
- Category examples: `Requirement`, `Architecture`, `ADR`, `Implementation`, `Testing`, or `Operations`
- Tag examples: `.net`, `angular`, `api`, `sql`, `azure`, `security`, `day-01`
