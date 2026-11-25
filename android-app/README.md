# KAWREXAM Student (Android)

Kotlin + Jetpack Compose + Room + DataStore + WorkManager + Retrofit.

- Backend URL: `https://kwr.up.railway.app` (change with `-PapiBaseUrl=...` when building).
- Build: GitHub Actions workflow `android-build.yml` (Actions tab > Android build > Run workflow). The APK is in the run's artifacts.
- Local build (Android Studio): open this folder; or `gradle assembleDebug` with Gradle 8.10+ and JDK 17.
- Release signing is optional: add repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them the release APK is signed with the debug key (fine for sideloading).

## Exam protection on a personal phone (no Device Owner)
Screen pinning, `FLAG_SECURE`, Do Not Disturb (alarms only), overlay blocking, and leave detection. Leaving the exam is a violation; the server validates it and applies the penalty. This reduces and records cheating; it cannot prevent a second phone.
