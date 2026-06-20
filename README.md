# SMS Spend — build your APK from your phone (no computer)

This builds an Android app that reads your bank SMS, auto-categorizes
spending, lets you tap any transaction to re-categorize it, and shows a
home-screen widget with this month's total.

## Build it (all from the phone browser)

1. Go to github.com, sign in (free account is fine).
2. Tap **+ → New repository**. Name it `sms-spend`, set it Private, tap
   **Create repository**.
3. On the repo page tap **Add file → Create new file**.
4. In the filename box type exactly:
   `.github/workflows/build-apk.yml`
   (the slashes create the folders automatically)
5. Paste the entire contents of `build-apk.yml` into the editor.
6. Scroll down, tap **Commit changes**.
7. Tap the **Actions** tab. A run called "Build APK" starts on its own.
   Wait ~3–5 minutes until it shows a green check.
8. Open the finished run, scroll to **Artifacts**, tap **SmsSpend-apk**
   to download the zip. Extract it — inside is `app-debug.apk`.

## Install it

1. Open the apk from your Files/Downloads.
2. Android will ask to allow installing from this source — allow it.
3. Open the app, tap **Allow** when it asks for SMS permission.
4. Long-press your home screen → Widgets → find **SMS Spend** → drag it out.

## Notes
- It only reads SMS on your device; nothing is uploaded anywhere.
- Tap any transaction in the list to change its category. Your changes stick.
- The widget refreshes when you open the app (and every ~30 min).
- Keyword rules live in `SmsParser.kt` if you want to tweak categories later.
