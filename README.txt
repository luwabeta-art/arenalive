ArenaPH Live: Android app with REAL screen sharing (like TikTok / Facebook Live)

WHY: phone browsers cannot capture the screen. This app wraps your ArenaPH page in a native Android shell that
captures the screen with Android's MediaProjection and feeds it into the broadcast. Pick "LIVE" in the app and it works.

BUILD THE APK (no Android Studio, free, about 5 minutes):
1. Create a free GitHub account, make a new repository, and upload ALL files in this folder (keep the folders, including .github).
2. Open the repository's Actions tab, run "Build APK" (it also runs by itself on upload).
3. When it finishes, open the run, download the artifact "ArenaPH-Live-APK", unzip it, and install app-debug.apk on your phone
   (allow "install unknown apps").

USE IT: open ArenaPH Live, log in, start a match broadcast, choose LIVE. Android asks "Start recording or casting?" -> Start now.
The app drops into a small floating window and keeps broadcasting. Open your game. Pause/resume etc. from the floating window.

UPDATING: replace app/src/main/assets/index.html with a newer ArenaPH file and rebuild.
