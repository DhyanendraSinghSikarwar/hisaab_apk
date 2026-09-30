# Hisaab

An offline-first personal finance app for Android. It reads bank transaction alerts from SMS and
email, parses them with rules (no AI, no API keys), and records each transaction once, even when
an SMS and an email both describe it.

All data stays on the phone: there is no account, no server and no analytics. The only network
traffic is to your email provider, and only after you connect email.

## Install the APK

A ready-built APK is in `apk/`:

| File | Use |
|------|-----|
| `apk/Hisaab-1.1.0.apk` | Release build: R8 full mode, shrunk. **Install this one.** |
| `apk/Hisaab-1.1.0-debug.apk` | Debug build, for development. |

To install:

1. Copy the APK to your phone.
2. Open it and allow **Install unknown apps** for your file manager.
3. Or, over USB with debugging on: `adb install -r apk/Hisaab-1.1.0.apk`

On first launch, tap **Allow SMS access**. The app scans the inbox, and new bank SMS appear as they arrive.

The included release APK is signed with the **debug key**, which is fine for installing on your own
phone. Sign it with your own key before sharing it or publishing it (see
[Release signing](#release-signing)). Signing a different way later means uninstalling first,
because Android refuses an update signed with another key.

## What's new in 1.1.0

- **Card spends made online now count.** The SMS reader used to drop every sender without a dedicated
  parser, so the generic bank parser never ran for SMS. It now also reads banks and card issuers such as
  IndusInd, AMEX, HSBC, RBL, OneCard and Standard Chartered, and understands alerts worded
  "Transaction of Rs…", "Txn Rs…" and "has been used for…". OTP messages are still ignored.
- **Connect email by address.** Enter your email, sign in with an app password, and type in the 6-digit
  code Hisaab mails you. Works with Gmail, Outlook, Yahoo, iCloud, Zoho and most IMAP providers, and
  needs no Google Cloud project. Google sign-in is still there as an advanced option.
- **One fetch window for SMS and email.** **Settings → Fetch history** (7, 30, 90, 180 or 365 days) sets how
  far back both the SMS scan and email sync read.
- **Monthly totals.** Home shows spent and income for one calendar month, with arrows to step back through earlier months.
- **Set an account's balance (optional).** **Accounts → edit** takes a current balance (or a card's available limit).
  Later transactions move it on, and a newer balance from a bank message replaces it.
- **The SMS permission card goes away** once access is granted, including when it was granted in system
  settings. It offers **Open App settings** when Android won't show the prompt, and **Not now** to hide it.

## What it does

| Area | Features |
|------|----------|
| SMS | Inbox scan in a WorkManager job (batches of 500, parsed in parallel, one database transaction per batch); real-time parsing of new SMS; only known bank senders are read |
| Email | By address: IMAP with an app password, verified by a 6-digit code sent to the address; the password is sealed with an Android Keystore key. Or Gmail API: sign-in through Credential Manager and AuthorizationClient; `gmail.readonly` scope; server-side sender filter; first sync, then incremental sync with `history.list`; hourly background sync plus **Sync now**; text and HTML bodies; PDF attachments |
| Parser | 10 banks (HDFC, ICICI, SBI, Axis, Kotak, IDFC First, Yes, BoB, PNB, AU) for both SMS and email, plus a fallback for other banks. It rejects OTPs, promotions, failed transactions, reminders and mandate setups |
| Dedup | Reference number, then SHA-256 hash, then a near-duplicate match within ±30 minutes. Duplicates are merged, and the record keeps every source. Unsure matches go to a review screen with **Merge**, **Keep both**, and **Split** on the detail screen |
| Screens | Home, Transactions (search and filters), transaction detail, Analytics (pie, monthly bars, daily line), Accounts, Budgets, Review, Settings, parser test tool |
| Other | Home-screen widget (today and this month), fingerprint or screen-lock app lock, CSV export and import, dark mode, Material You colours |

## Test results

Final run on the completed code: **375 tests, 0 failures**.

| Suite | Where it runs | Tests |
|-------|---------------|------:|
| `:parser-core`: 10 bank corpora, rejection rules, extractors, hash, registry, dedup scenarios, accuracy | JVM | 337 |
| `:shared`: merge rules | JVM | 2 |
| `:email`: MIME and HTML decoding, query, sync engine against a mocked Gmail API | JVM | 12 |
| `:app`: CSV round trip, SMS id consistency | JVM | 5 |
| `:shared`: DAOs, index use, dedup against real Room, review merge/keep/split, balances, schema migration | Android 36 emulator | 15 |
| `:app`: SMS worker (1,200 messages, non-bank messages skipped, re-scan adds nothing, incremental scan), Home screen UI | Android 36 emulator | 4 |

Measured results:

- Field accuracy on the included corpus: 940/940.
- No negative sample parsed.
- A combined SMS and email corpus produces 0 duplicates.
- Parsing: about 75 µs per message on a desktop JVM, single thread. 10,000 messages take about 0.75 s.
- Cold start of the release build on the emulator: 1.3 s.

The test messages were written in each bank's alert format; they aren't real inbox messages. Add
masked real messages to `parser-core/src/test/kotlin/com/hisaab/parser/corpus/` to measure accuracy on your own mail.

## Setup

### 1. Java

Any Java 17 or newer is enough to start Gradle. The build downloads JDK 17 itself into `~/.gradle/jdks`.

```bash
java -version                                              # check
sudo apt update && sudo apt install -y openjdk-21-jdk      # only if missing
```

### 2. Android SDK

Either install [Android Studio](https://developer.android.com/studio) and open this folder, or use the command-line tools:

```bash
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-9862592_latest.zip
unzip commandlinetools-linux-*_latest.zip && mv cmdline-tools latest
yes | ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --licenses
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-37.0" "build-tools;37.0.0" "platform-tools"
```

Then create `local.properties` in the project root:

```properties
sdk.dir=/home/<you>/Android/Sdk
```

### 3. Build

```bash
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug        # build and install on a connected phone
```

The first build downloads Gradle 9.4.1, the dependencies and JDK 17. It needs internet and takes several minutes.

### 4. Run the tests

```bash
# JVM tests, no device needed
./gradlew :parser-core:test :shared:testDebugUnitTest :email:testDebugUnitTest :app:testDebugUnitTest

# Parser speed checks (timing-sensitive, so run separately)
./gradlew :parser-core:perfTest

# Instrumented tests: need a phone with USB debugging, or a running emulator
./gradlew :shared:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

To create a headless emulator for the instrumented tests (needs KVM):

```bash
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager "emulator" "system-images;android-36;google_apis;x86_64"
echo no | ~/Android/Sdk/cmdline-tools/latest/bin/avdmanager create avd -n hisaab36 -k "system-images;android-36;google_apis;x86_64" -d pixel_6
~/Android/Sdk/emulator/emulator -avd hisaab36 -no-window -no-audio -gpu swiftshader_indirect -memory 3072 &
```

With the emulator running, you can send it a bank SMS:
`adb emu sms send HDFCBK "Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 26/09/26 Ref 526912345678"`.

Test reports are written to `<module>/build/reports/`.

### 5. Email (optional)

SMS works without this. The simple way needs no setup: **Settings → Email → Connect**, enter your address,
sign in with an app password (Gmail: [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords);
2-Step Verification must be on), then type the 6-digit code Hisaab emails you. The first sync starts at once and
then runs every hour.

The rest of this section is only for the advanced **Google sign-in** option, which uses the Gmail API.
It needs a Google Cloud OAuth client tied to your signing key.

`gmail.readonly` is a restricted scope. It works for your own account and for up to 100 test users you list.
A public Play Store release needs Google's OAuth verification and a third-party security assessment.

1. Open the [Google Cloud Console](https://console.cloud.google.com/) and create a project, for example `hisaab`.
2. **APIs & Services → Library**: search for **Gmail API** and click **Enable**.
3. Configure the consent screen, under **OAuth consent screen** or **Google Auth Platform**:
   - Audience: **External**, publishing status **Testing**.
   - Fill in the app name and support email.
   - Under **Data access**, add the scope `https://www.googleapis.com/auth/gmail.readonly`.
   - Under **Test users**, add every Gmail address that will sign in.
4. Get the SHA-1 of the key that signs your APK:

   ```bash
   # The included APKs and debug builds use the debug key:
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
   # Or, for any build variant:
   ./gradlew :app:signingReport
   ```

5. **Credentials → Create credentials → OAuth client ID**:
   - Type: **Android**.
   - Package name: `com.hisaab`.
   - SHA-1: the value from step 4.
   - Make one client for each signing key you use.
6. Optional: create another OAuth client ID with type **Web application**, and put its ID in
   `local.properties`. It enables the one-tap Credential Manager sign-in. Without it, the app goes
   straight to Google's account picker and consent screen, and sync works the same.

   ```properties
   GOOGLE_WEB_CLIENT_ID=1234567890-abcdef.apps.googleusercontent.com
   ```

   Rebuild after changing this.
7. In the app, go to **Settings → Email → Connect → Advanced: Google sign-in** and approve read access. The first sync starts at once.
   After that it runs every hour on any network, and **Sync now** runs it on demand.

Other email settings:

- **Fetch history** (top of Settings): 7, 30, 90, 180 or 365 days, for both SMS and email.
- **Bank senders:** edit the whitelist; one address or domain per line.
- **Read PDF statements:** on or off. Password-protected PDFs are skipped.
- **Disconnect / Sign out:** deletes the encrypted app password or token and its Android Keystore key, and forgets the account.
  Transactions that were already found stay.

### Release signing

Create a key once:

```bash
keytool -genkeypair -v -keystore hisaab-release.jks -alias hisaab -keyalg RSA -keysize 4096 -validity 10000
```

Then add it to `local.properties`, which is kept out of git:

```properties
RELEASE_STORE_FILE=hisaab-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=hisaab
RELEASE_KEY_PASSWORD=...
```

`./gradlew :app:assembleRelease` now signs with that key. If it isn't set, it falls back to the debug
key. For Gmail, register this key's SHA-1 as an additional Android OAuth client.

## Project layout

```
gradle/libs.versions.toml   every dependency version
parser-core/                pure Kotlin; runs on the plain JVM
  bank/                     BaseBankParser pipeline + 10 bank parsers + generic fallback
  registry/                 sender -> parser HashMap, SMS pre-filter, Gmail whitelist
  rules/  extract/  merchant/  hash/
  dedup/DuplicateMatcher    reference -> hash -> ±30 min near-duplicate match
shared/                     Room database (bundled SQLite), DAOs, TransactionRepository (the only write path)
  schemas/                  exported schema JSON, checked by MigrationTest
email/                      Gmail: Ktor API client, MIME/Jsoup/PdfBox, Keystore token store, auth, sync engine, worker
app/                        Compose UI, Hilt, SMS worker and receiver, Glance widget, CSV, app lock
```

## How dedup works

Every incoming message goes through one database transaction:

1. **Already seen?** An SMS id (a hash of sender, send time and body) or a Gmail message id is never processed twice.
2. **Same reference number:** a UPI, IMPS or NEFT reference with the same amount and direction is a duplicate.
3. **Same hash:** `SHA-256(amount | direction | last4 | date | ref)`. From different sources it's a duplicate. From the same
   source without a reference it goes to review, because two ₹120 coffees on one card in a day are two transactions.
4. **Near-duplicate:** same amount and direction, same account or merchant, within ±30 minutes. SMS and email merge automatically.
   Same-source matches, and SMS/email matches later the same day, go to the **Possible duplicates** review.

A duplicate is **merged**, not dropped:

- Gaps are filled: the merchant usually comes from the email, the balance from the SMS.
- Every source is kept in `transaction_sources`.
- **Split** on the detail screen re-parses one source into its own transaction if a merge was wrong.

The hash uses money *direction* (out, in or transfer) rather than the exact type. One source may read
a payment as an investment ("ZERODHA") while the other only says "ACH". Both are money out, so they must hash the same.

## Known limits

- **Gmail sign-in hasn't been tested against a live Google account** here; that needs your Cloud project.
  The sync engine is tested against a mocked Gmail API: paging, 8 requests at a time, history sync, the fallback when history has expired, and token refresh.
- **Bank statement PDFs** are usually password-protected and are skipped. For unlocked PDFs, each line
  goes through the SMS/email parser, which is built for alerts, not statement tables.
- **Baseline Profile:** ProfileInstaller is included, but no profile has been generated. That needs a
  macrobenchmark module run on a device.
- **SMS permission:** Google Play allows `READ_SMS` only for apps whose main purpose needs it.
  Installing the APK directly isn't affected.
- **Speed figures** come from a desktop JVM and an emulator. Check them on a mid-range phone.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `SDK location not found` | Create `local.properties` with `sdk.dir=...` (setup step 2). |
| `Cannot find a Java installation ... languageVersion=17` | The JDK download failed. Check your internet connection, or install `openjdk-17-jdk`. |
| `does not provide the required capabilities: [JAVA_COMPILER]` | `JAVA_HOME` points to a Java runtime without the compiler. Unset it or install a full JDK. |
| `App not installed` when updating | The installed copy is signed with a different key. Uninstall it first. |
| Gmail: "Sign in again" | Google revoked the grant, or the SHA-1 or package name doesn't match the OAuth client. |
| Gmail: consent screen says "app not verified" | Expected in Testing mode. Continue, and make sure the account is listed under Test users. |
| No SMS transactions | Check SMS permission, and use Settings → Rescan. Only bank and card-issuer senders are read. |
| SMS permission greyed out | Android restricts SMS for apps installed from an APK: App info → ⋮ → **Allow restricted settings**, then allow SMS. |
| A card spend is still missing | Paste the SMS into **Settings → Test the parser** to see why, and add the sender or wording to the parser. |
| Email: "Sign-in was refused" | Use an app password, not your normal password. Gmail needs 2-Step Verification on to create one. |
| No verification code | Check spam. The code is sent from your own address to itself; tap **Resend code**. |
| Build killed (exit 137) | Out of memory. Close the emulator while building, or lower `org.gradle.jvmargs`. |
