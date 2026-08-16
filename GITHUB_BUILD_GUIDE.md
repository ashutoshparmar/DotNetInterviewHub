# Build the APK using GitHub

You do not need Android Studio, VS Code, Java, Gradle, or the Android SDK on your laptop for this method. GitHub performs the build online.

## Part 1 — Create the repository

1. Sign in at `https://github.com`.
2. Click the **+** button in the upper-right corner and select **New repository**.
3. Enter the repository name `DotNetInterviewHub`.
4. Select **Private** if you do not want the source and interview content to be publicly visible.
5. Do not add a README, `.gitignore`, or licence because the project already contains them.
6. Click **Create repository**.

## Part 2 — Upload the project

1. Extract `DotNetInterviewHub_Android_Studio_Project.zip` on your computer.
2. Open the extracted `DotNetInterviewHub` folder.
3. On the empty GitHub repository page, click **uploading an existing file**.
4. Drag all items from inside the `DotNetInterviewHub` folder into the upload area. Upload the contents, not the outer folder itself.
5. Confirm that `.github`, `app`, `build.gradle`, `settings.gradle`, and `README.md` are included.
6. Enter `Initial Android app` as the commit message and click **Commit changes**.

The first upload to the `main` or `master` branch starts the APK build automatically.

## Part 3 — Download the APK

1. Open the repository's **Actions** tab.
2. Select **Build Android APK** in the left column.
3. Open the newest workflow run and wait for the green check mark.
4. Scroll to the **Artifacts** section.
5. Click **DotNetInterviewHub-debug-apk** to download it.
6. Extract the downloaded artifact ZIP. It contains `app-debug.apk`.

## Part 4 — Install it on Android

1. Transfer `app-debug.apk` to the Android phone or download it directly on the phone.
2. Open the APK from the Files or Downloads app.
3. If Android asks, allow **Install unknown apps** for the browser or file manager you used.
4. Tap **Install**.

The debug APK is suitable for personal installation and testing. It is not the signed release package required for publishing to Google Play.

## Build it again after changing the source

Every new commit to `main` or `master` automatically creates a fresh APK. You can also open **Actions → Build Android APK → Run workflow** to start a build manually.

## If the Actions tab shows no workflow

Check that the workflow exists at this exact repository path:

`.github/workflows/build-apk.yml`

The `.github` folder must be at the repository root, beside the `app` folder—not inside another `DotNetInterviewHub` folder.
