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
| `apk/Hisaab-1.4.0.apk` | Release build: R8 full mode, shrunk. **Install this one.** |

To install:

1. Copy the APK to your phone.
2. Open it and allow **Install unknown apps** for your file manager.
3. Or, over USB with debugging on: `adb install -r apk/Hisaab-1.4.0.apk`

On first launch, tap **Allow SMS access**. The app scans the inbox, and new bank SMS appear as they arrive.
If Android blocks it, see [Allow SMS access](#1-allow-sms-access) below.

The included release APK is signed with the **debug key**, which is fine for installing on your own
phone. Sign it with your own key before sharing it or publishing it (see
[Release signing](#release-signing)). Signing a different way later means uninstalling first,
because Android refuses an update signed with another key.

## Set up on your phone

### 1. Allow SMS access

Tap **Allow SMS access** on the Home screen and choose **Allow**.

On Android 13 and later, an app installed from an APK file (not the Play Store) may get the message
**"App was denied access"** or show SMS greyed out. This is Android's *restricted settings* protection. Unlock it once:

1. Open the phone's **Settings → Apps → Hisaab** (App info).
2. Tap **⋮** (top right) → **Allow restricted settings**, and confirm with your fingerprint or PIN.
3. On the same screen: **Permissions → SMS → Allow**.
4. Open Hisaab again. The permission card disappears on its own and the SMS scan starts.

If **Allow restricted settings** isn't in the ⋮ menu, tap **Allow SMS access** in Hisaab once more so Android shows
the block message, then go back to App info; the option appears after that. On Xiaomi, Redmi and POCO phones, also check
**Settings → Apps → Manage apps → Hisaab → Other permissions**. If the option never appears, install the APK from
**Files by Google** or with `adb install -r Hisaab-1.4.0.apk`; those installers are not restricted.

### 2. Connect your email (optional)

Hisaab reads bank alert emails too. It signs in with an **app password**: a separate 16-character password your
email provider creates for one app. Google and most providers don't let other apps use your normal password, so:

- Hisaab never sees your real password.
- The app password is stored encrypted on the phone only (sealed with an Android Keystore key).
- You can delete it at any time, and Hisaab loses access at once.

**Create a Gmail app password (about 2 minutes):**

1. **Turn on 2-Step Verification.** Google only offers app passwords when it is on.
   Go to [myaccount.google.com/security](https://myaccount.google.com/security) → *How you sign in to Google* →
   **2-Step Verification** → turn it on
   ([Google's help page](https://support.google.com/accounts/answer/185839)).
2. **Create the app password** at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords).
   Sign in if asked, type a name such as `Hisaab`, and tap **Create**
   ([Google's help page](https://support.google.com/accounts/answer/185833)).
3. **Copy the 16-letter password** (like `abcd efgh ijkl mnop`). Google shows it only once. Spaces don't matter.

**Connect in Hisaab:**

1. **Settings → Email → Connect**, enter your email address, and tap **Continue**.
2. Paste the app password and tap **Sign in & send code**.
3. Open your mailbox: an email titled **"Hisaab verification code: 123456"** arrives from your own address.
   Type the 6 digits into Hisaab and tap **Verify**. The code expires after 10 minutes; **Resend code** sends a new one.
4. The first fetch starts at once and covers the **Fetch history** period (Settings, 7 to 365 days). After that it runs every hour.

**App password pages for other providers:**

| Provider | Create an app password |
|----------|------------------------|
| Gmail | [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) (2-Step Verification on first) |
| Outlook, Hotmail, Live | [account.live.com/proofs/AppPassword](https://account.live.com/proofs/AppPassword) (two-step verification on first) |
| Yahoo Mail | [login.yahoo.com/account/security](https://login.yahoo.com/account/security) → *Generate app password* |
| iCloud Mail | [account.apple.com](https://account.apple.com/account/manage) → *Sign-In and Security* → *App-Specific Passwords* |
| Zoho Mail | [accounts.zoho.com](https://accounts.zoho.com/home#security/app_password) → *Security* → *App Passwords* |

The **Create an app password** button in the Hisaab dialog opens the right page for your provider.

**If something goes wrong:**

| Problem | Fix |
|---------|-----|
| The app passwords page says the setting isn't available | Turn on 2-Step Verification first. Work and school accounts are often blocked by their administrator; use a personal account. |
| "Sign-in was refused" | You entered your normal password, or the app password was mistyped. Create a new one and paste it. |
| No verification email | Check Spam and the Promotions/Updates tabs, then tap **Resend code**. |
| "Sign in again" later on | The app password was deleted or changed. Create a new one and connect again. |

**To disconnect:** **Settings → Email → Disconnect** deletes the stored app password. To revoke access from Google's
side as well, delete the "Hisaab" entry at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords).
Transactions already found stay in the app either way.

### 3. UPI payments without an SMS (optional)

Many banks don't send an SMS for small UPI payments, but GPay, PhonePe, Paytm and the rest always show a notification.
Turn on **Settings → UPI & payment apps → Read payment app notifications**, then allow Hisaab under **Notification access**
(if it's greyed out, use **Allow restricted settings** as in step 1).

- Only known payment and bank apps are read; every other app's notifications are ignored.
- Only completed payments count. Offers, cashback, requests, reminders, OTPs and failures are skipped.
- When the bank's SMS or email for the same payment arrives later, it is merged into the same transaction, not counted twice.

You can also add any payment yourself: **+** on Home or **Add** in Transactions. **Fill from a screenshot** reads a payment
receipt on the phone (Google ML Kit, offline) and fills in the form for you to check.

### 4. Statements and investments (optional)

**Settings → Statements & passwords** handles statement PDFs:

- PDFs attached to emails from your banks, card issuers, CAMS, KFintech, NSDL, CDSL and brokers (including INDmoney, Zerodha
  and Groww) are read automatically once email is connected. **Import a statement PDF** reads one from your phone.
- Most statements are password protected. Add the password once under **Saved passwords**; it is tried on every new
  statement. A statement no saved password opens shows **Unlock**. Common formats:
  - Credit cards: first 4 letters of your name + DDMM of birth (e.g. `RAHU0105`).
  - CAS (CAMS, KFintech, NSDL, CDSL): your PAN in capitals.
  - Bank statements: often your customer ID or date of birth.
- Card and bank statement rows become transactions, and rows already recorded from an SMS or email are merged, not doubled.
  A CAS or demat statement becomes holdings.

**Settings → Investments** (or the Investments card on Home) shows EPF, mutual funds, shares, ETFs and anything you add:

- **EPF:** the balance from EPFO's passbook SMS.
- **Mutual funds and shares:** units and market value from your CAS. Request one at
  [camsonline.com](https://www.camsonline.com/Investors/Statements/Consolidated-Account-Statement) (choose the PDF password; it
  is emailed to you), or use the monthly NSDL/CDSL CAS.
- **Anything else** (NPS, PPF, gold, FDs, US stocks): tap **Add** and enter the value. A newer statement updates holdings it covers.

## Privacy: what stays on the phone

Everything Hisaab stores stays in the app's private storage on this phone: transactions, the full SMS and email text,
statements, investments, and passwords. Nothing is uploaded, and there is no Hisaab server or account.

| What | Where it lives | Leaves the phone? |
|------|----------------|-------------------|
| Transactions, messages, investments | App-private database | No. Cloud backup and device transfer are switched off. |
| Email app password, statement passwords, Google token | Encrypted with an Android Keystore key that can't be exported | No |
| Locked statement PDFs | App-private, no-backup folder; deleted once unlocked | No |
| Screenshots and PDFs you pick | Read on the phone; never copied out | No |

The only network traffic is to your own email provider, after you connect email: Hisaab downloads bank emails and statements,
and sends one verification email to yourself. All connections are encrypted; plain-text connections are blocked. Google's
on-device text reader (ML Kit) normally reports anonymous usage counts to Google; Hisaab removes that uploader, so those
counts are dropped on the phone.

## Releases and in-app updates

Every push to `main` runs the GitHub Actions pipeline in `.github/workflows/release.yml`:

1. **Test:** all JVM suites (parser, database, email, app).
2. **Release, only if every test passed and the version is new:** build the release APK, sign it with the Hisaab key
   (plus the rotation proof from the old debug key), and publish it as a GitHub Release `v<versionName>` with its SHA-256
   in the notes.

To ship a new version, raise `versionCode` and `versionName` in `app/build.gradle.kts` and push to `main`. Pushes that
don't change the version only run the tests. `ci.yml` runs the same tests on other branches and pull requests.

The app checks the latest release once a day (**Settings → About & updates**, or the banner on Home), downloads the APK,
checks its SHA-256, and opens Android's installer. Android refuses any APK not signed with the Hisaab key, so a tampered
download can't be installed. The first time, Android asks you to allow Hisaab to install apps.

**One-time setup: repository secrets** (GitHub → the repo → Settings → Secrets and variables → Actions → New repository secret).
The base64 files are in `C:\Users\11624\keys` on the build PC:

| Secret | Value |
|--------|-------|
| `HISAAB_RELEASE_KEYSTORE` | contents of `hisaab-release.jks.b64.txt` |
| `HISAAB_KEYSTORE_PASSWORD` | `RELEASE_STORE_PASSWORD` from `signing.properties` |
| `HISAAB_DEBUG_KEYSTORE` | contents of `debug.keystore.b64.txt` |
| `HISAAB_KEY_LINEAGE` | contents of `lineage.bin.b64.txt` |

Secrets are encrypted by GitHub and never shown in logs. Without them the tests still run, but nothing is released.

## What's new in 1.7.0

- **In-app updates** from GitHub Releases, published by the CI/CD pipeline after all tests pass (see above).
- **New-transaction notifications** with the predicted category; expand one to pick another category without opening the app.
- **Hisaab remembers your categories:** once you set a category for a merchant, its future transactions (and older
  uncategorised ones) get it automatically.
- **Several email addresses** can be connected; statements and alerts from all of them are read.
- **Statements update your accounts:** a card statement sets the card's available limit (limit minus amount due), a bank
  statement sets the account's closing balance. **Try all passwords** re-checks every locked statement.
- **Remove an account or card from view** (edit → Remove from view) and show it again from the bottom of Accounts.
- **Hide amounts** (eye button on Home, or Settings → Privacy screen), a **Budgets** card on Home, and a floating bottom bar.

## What's new in 1.4.0

- **"Statement detected" notification:** when a statement arrives by email that no saved password opens, Hisaab says so
  ("HDFC Bank statement detected") and the tap goes straight to its password box. Home shows the same as a banner.
- **Statement pages:** each statement opens to its details. Card statements show total due, minimum due, due date and credit
  limit; bank statements show money in and out; every statement lists its transactions. Statements are grouped as credit card,
  bank and investment.
- **Investments is a tab** in the bottom bar, with EPF, NPS, Mutual funds, Stocks & ETFs and Other. NPS balances are read from
  NPS SMS. Budgets moved to Home ("Where it went → Budgets").
- **ⓘ buttons** explain the important settings: connecting email, app passwords, SMS, payment-app notifications, fetch
  history, statement passwords, investments, card linking, manual balances and duplicates.
- **New Home summary card**, and a smaller APK (phones only, no emulator code).
- Signed with Hisaab's own release key instead of Android's shared debug key; it still installs over earlier versions.

## What's new in 1.3.0

- **Statement PDFs** from email or your phone, with saved passwords and **Unlock** for locked ones (see above).
- **Investments:** EPF from EPFO SMS; mutual funds, shares and ETFs from CAS and broker statements; and holdings you add.
- **Full message text** on every transaction: the whole SMS or email, or the statement line, is shown and can be selected and copied.
- **Privacy:** ML Kit's usage reporting is switched off, plain-text network connections are blocked, and the privacy
  details are listed in Settings and above.

## What's new in 1.2.0

- **Category popup:** one sheet with icons, grouped (Food & drink, Shopping & lifestyle, Transport & travel, Home & bills,
  Health & education, Money, Income), with search. New categories: Subscriptions, Personal Care, Household, Gifts, Donations,
  Taxes, Bank Fees & Charges.
- **Accounts and cards are separate tabs**, each with the bank's logo and a colour by kind (blue accounts, orange credit cards,
  purple debit cards). Set an account's type (Savings, Current, Salary, NRE, NRO, Wallet, FD, RD, PPF, Loan) and a card's type
  and network (Visa, Mastercard, RuPay, American Express, Diners Club, Discover, JCB, Maestro, UnionPay). FD, RD, PPF and loans
  are left out of the Home balance.
- **Debit cards link to their bank account.** The Cards tab suggests the link, using the balance a debit-card SMS reports.
  A linked card's spends and withdrawals count against that account, and its SMS balance updates the account.
- **Debit-card withdrawals** ("Withdrawn Rs.10000 From HDFC Bank Card...") are recorded as cash withdrawals.
- **Cards that print only two digits** ("SBI Card number ending with 96") are now read. Foreign-currency spends are kept in their
  own currency and are not added to the ₹ totals.
- **Bulk actions:** long-press a transaction to select, then change the category or delete. Deleted transactions don't come back on a rescan.
- **Duplicates:** dates on every row, tap to open each one, and **Compare** shows both side by side with their original messages.
- **Add a transaction** by hand or from a screenshot, and **read UPI payments from payment-app notifications** (see above).

Bank and card-network logos are single-colour icons from [Simple Icons](https://simpleicons.org) (CC0). Banks it doesn't cover
are shown as a monogram in the bank's colour. The logos are trademarks of their owners.

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

SMS works without this. The usual way to connect email needs no developer setup; see
[Connect your email](#2-connect-your-email-optional) under "Set up on your phone".

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
