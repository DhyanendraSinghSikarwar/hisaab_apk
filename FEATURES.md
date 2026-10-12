# DhanKosh features

Current feature reference for DhanKosh (formerly Hisaab), an offline-first personal finance app for Android.
Release history is in [CHANGELOG.md](CHANGELOG.md).

## Overview

- Offline-first. Everything is stored on the phone; there is no account, server, analytics or remote logging.
- Network use is limited to your email provider (after you connect email), update checks against GitHub Releases, optional language-pack downloads, market news feeds, and the payment hand-off on the Support page.
- Transactions are built from what the phone already receives:

| Source | How it is read |
|---|---|
| Bank and card SMS | Rule-based parsing; no AI and no API keys |
| Email | IMAP with an app password, or Gmail sign-in; alert mails and statement attachments |
| Payment-app notifications | PhonePe, GPay, Paytm and others, plus bank apps such as YONO SBI |
| Statements | PDF (including password-protected), Excel and CSV |
| Manual entry | Transactions, accounts, cards, loans, investments, recurring items, screenshots |

- Each payment is recorded once, even when an SMS, an email, a notification and a card alert all describe it.
- Bottom bar: Home, Transactions, Portfolio, Analysis, More.
- A global **Book** (Personal, Business, All) and a **Period** filter apply across the app.

## Home

Sections can be shown, hidden and reordered (More > Customise, or the edit mode on Home). Presets: Minimal, Spender, Investor, Business, Everything.

| Section | Contents |
|---|---|
| Spent this month | Total for the selected Period and Book, change against the previous period, running line, income, expenses, saved, and a foldable account and card list |
| Credit card dues | Credit cards only, with the amount to pay, status line, due date and a Mark paid button |
| Cash flow | Income against spend and savings rate |
| Upcoming | Bills, EMIs, insurance renewals and card dues in the coming days |
| Insights | Notable changes and warnings from your data |
| Spend by category | Donut chart with drill-down into sub-categories |
| Recent transactions | Latest entries |
| Budgets | Hidden by default; rings for each category budget |

- Greeting header with your name, a notification bell, a sync button and an add button.
- Amounts can be masked.

## Transactions

- Views: List, Calendar and By merchant.
- Summary strip with Income, Expenses and Net, coloured.
- Filters: type, bank, source and category; search with match count.
- Review banner for transactions that need attention.
- Detail page: merchant, time, category and sub-category, method, account, source messages (SMS, email, notification), note and the rupee value for foreign-currency spends.
- Edit category and sub-category; changing a category teaches a rule for that merchant.
- Account row is tappable to change the account, with Remember for this payee.
- Merge two transactions by hand and split them again.
- Multi-select to delete or change category in bulk.
- Add a transaction manually, or from a screenshot.
- Brand logos for banks, payment apps and merchants (about 140 bundled logo files, offline).
- Credit card bill payments are shown but never counted as spent or income.
- Transfers between your own accounts and cards are not counted as spend.

### Categories

- Every category has sub-categories with icons; known merchants are filed automatically.
- Custom categories and sub-categories with an icon picker.
- Rules (More > Rules): match a merchant or UPI ID exactly or by text, choose a category and sub-category, and optionally apply to past transactions. Your rules override automatic ones; rules are also learned from your edits.

## Accounts and cards

- Bank accounts, credit cards, debit cards, deposits and loans, created automatically from messages or added by hand. Phone numbers never become accounts.
- Balances are recomputed from every SMS, notification, statement and manual entry; a newer bank-stated balance replaces an older figure.
- A user-set balance is carried forward by later transactions.
- Personal or Business marking per account.
- Nickname, colour, card network logo and Business badge.
- Debit cards can be linked to a bank account.
- Account detail page with spend and income lines over 6 months, a year or all time.

### Credit cards

- Total outstanding, billed amount, minimum due, due date, unbilled spend, credit limit and limit left.
- Figures come from statements, SMS and email; an estimated bill is used when none arrives. Spends raise unbilled and lower the limit left.
- Edit amounts: total, billed, minimum due, due date and unbilled, each showing the calculated value with a reset. A newer statement or a full payment replaces manual edits.
- Mark paid, and bill reminders 5 days and 2 days before and on the due date, with Remind tomorrow.
- Only credit cards are listed; a card of unknown type counts when its alerts show a credit limit.
- Forex markup per card, for spends in other currencies.

### Managing accounts

- Merge duplicates into one display group; nothing is moved or deleted, and Unmerge restores them. A Restore missing accounts action recovers hidden ones.
- Hide (keeps transactions, undoable) or delete (removes the account and optionally its transactions).
- Bank unlock details per account: customer ID, account number, IFSC, name, mobile and email, stored encrypted on the phone and inherited from the profile; used to open that bank's statements.

## Payment tracking

- UPI and payment-app notifications are read in many layouts. Reasons for unreadable ones are listed in the Activity log, and a warning appears when notification access is off.
- A payment-app notification never creates an account: it waits for the bank message and adopts its account.
- De-duplication across SMS, email, notification and card alert, with the exact amount (paise kept) and payee name. Two real payments of the same amount are merged only within 30 minutes.
- Balances and card limits are applied once per payment, in either arrival order.
- Untracked payments: money that left or arrived with no message is detected from the next stated balance. The account page offers Add transaction, Assign to an existing notification row, or Ignore.
- Transfers between your own accounts and cards record both sides; a transfer to a credit card counts as a card payment.
- Credit card bill payments (SMS, UPI, NEFT, CRED, app notification) never count as spend or income.
- Promotional and balance lines in notifications are not read as the amount; OTP messages are rejected.

## Portfolio

- Portfolio value and gain.
- Net worth card with 1M to All ranges, a split by account with a legend, and a breakdown of assets and liabilities. History is recorded daily and estimated from transactions for earlier dates.
- Choose which money counts in net worth (Customise). Credit cards and their limits are listed but not counted.
- Asset allocation by class.
- Holdings by class: mutual funds, stocks, NPS, EPF, PPF, FDs, RDs and other deposits, with invested value, gain and return where a cost is known. Add or edit holdings by hand.
- Maturity calendar for the next 12 months (FD, RD and similar), with the maturity instruction.
- Loans section linked to the Loans page.
- Sections can be hidden and reordered.

### Statements and holdings sources

- Statements page lists every statement read, with matched rows, totals and Read again.
- Card, bank, CAS and broker statements from email or the phone (PDF, Excel, CSV).
- CAS (CAMS, KFin, CDSL-style) for funds and shares; NPS and EPF statements and SMS.
- Groww-style fund and SIP emails update holdings.
- Holdings files from Groww, Zerodha, Kuvera, INDmoney and other apps are read by column names, summed per scheme and checked against the sheet totals.
- Statements update card limits and bank closing balances, and a short notification appears when a statement shows a different balance.
- Foreign-currency totals use rates from the Forex rates page.

## Loans

- More > Loans and the Portfolio loans section.
- Detected from lender messages (banks and NBFCs) and EMI debits; or add a loan by hand.
- Detail: lender, amount, interest rate, tenure, first EMI, EMI, paid, outstanding, interest, next EMIs and an amortisation schedule.
- EMIs appear in Bills and subscriptions.
- Link a payment filed elsewhere to a loan; merge duplicate loans and unmerge them; mark a wrong detection as Not a loan.

## Analysis

| Section | Contents |
|---|---|
| Spending | Category donut with cross-filtering, 12-month bars with average, top merchants, a when-you-spend heatmap, budgets |
| Cash flow | Income against spend, savings rate, where income went |
| Forecast | Month-end spend projection and cash balance for the next 30 days |
| Compare | Current against previous period by category |

- Filters: period (this month, last month, this FY, all time, custom), Book, bank accounts or cards.
- Budgets: monthly limits per category with on-track and near-limit status.
- Sections can be hidden and reordered.

## Tax centre

- Period: this financial year, last financial year or the last 12 months.
- Income collected from tracked credits: salary, interest, dividends, rent and other income; refunds, own-account transfers and reimbursements are left out. With under 12 months of data the year is estimated (salary continues at the latest month's pay, other income scaled by months observed) and marked Estimated.
- New and old regime side by side: gross income, deductions, taxable income, tax, 87A rebate with marginal relief, surcharge with marginal relief, cess, effective rate and the recommended regime. No tax up to Rs 12 lakh taxable income in the new regime (about Rs 12.75 lakh salary with the standard deduction); capital gains at 111A and 112A rates stay outside the rebate.
- Expandable slab breakdown per regime. Old-regime deductions found in your data: 80C payments, life and health premiums, NPS.
- Rules are table-driven per year (FY 2024-25, 2025-26, 2026-27) in `TaxRules`; Budget 2026 left the rates unchanged, so 2026-27 uses the 2025-26 rules. Checked 2026-10-11 against incometax.gov.in (AY 2026-27 rates), Union Budget 2025 and 2026 coverage (BankBazaar, ClearTax, Tata Capital). Capital-gains rates and the old-regime rebate are carried over from FY 2025-26.
- What-if calculator (income lines, capital gains and deductions; never saved) and sharing as an HD image or PDF.

## Alerts and reminders

- New-transaction notifications with category buttons.
- Budget threshold alerts (set the percentage) and a notice when a budget is used up.
- Card bill reminders (5 days, 2 days, due date) with Mark paid.
- Recurring bills, subscriptions, income, EMIs and insurance renewals on a month calendar; low-balance alerts three days ahead.
- Recurring payments are detected after two months of repeats; add your own, monthly or yearly.
- Notification access and SMS access prompts with shortcuts to system settings.

## Data sources and sync

- SMS: scans the inbox and reads new messages as they arrive; sync also re-reads the last 14 days.
- Email: connect by address with an app password (verified by a 6-digit code) or by Google sign-in; several mailboxes; per-provider app-password guides.
- History to read: 7 to 365 days; changing it reads that period again.
- Payment-app notifications on or off.
- Editable bank sender and mail-domain lists.
- Automatic nightly email sync and SMS catch-up; a sync button with a progress pill.
- Activity log of what was read and why something was skipped.
- Import and export transactions as CSV; duplicates are skipped on import.
- Market news from several feeds (Today, Week, Month).
- Forex rates by date, used for foreign spends.

## Statements and passwords

- Locked PDFs show the email they arrived with as a hint.
- Saved passwords are tried on every locked and new statement; passwords that open one are remembered.
- Candidate passwords are built from profile details: name as per bank, alternate names, date of birth, PAN, mobile numbers, and per-account unlock details.
- Statement mails are picked by sender and subject (CAS, NPS, EPF, card and bank statements).
- Scanned images cannot be read.

## Profile, themes and languages

- Profile: name, photo with crop, zoom and rotate, email, phone, occupation, date of birth, PAN, names and mobiles as per bank.
- First-run sign-in by verified email or phone.
- Themes: DhanKosh, Fold, Classic, Emerald, Graphite, Indigo Night, Saffron, Ocean, and Hero and Cinema palette groups; light, dark and pure-black dark mode. No wallpaper colours.
- Text size setting.
- Languages: English plus seven downloadable packs (Hindi, Bengali, Telugu, Marathi, Tamil, Gujarati, Kannada). A pack can be removed after download.
- Customise tab sections: show, hide and reorder sections on Home, Analysis and Portfolio.

## Security and backup

- App lock with fingerprint, face or screen lock; the current screen is kept after unlocking.
- Mask balances and totals when the app opens.
- Email app passwords and bank unlock details are sealed with an Android Keystore key.
- Backup: export every transaction to CSV; import it back.
- In-app updates from GitHub Releases, verified by SHA-256; What's new lists the release notes.

## Support

- Support DhanKosh page: amounts of 100, 200, 500 or custom, and a Pay with a UPI app button. The UPI ID is not shown. The app stays free either way.

## Supported banks, lenders and apps

- Banks: public, private, small finance, foreign, payments and co-operative banks, with sender codes and mail domains. A generic parser reads alerts from senders without a dedicated template.
- Card alerts from the major issuers, including the newer Axis layout and Axis statement SMS.
- Lenders: banks and NBFCs (for example Propelld and Aditya Birla Capital).
- Payment apps: PhonePe, GPay, Paytm and others through notifications; bank apps such as YONO SBI.
- Investment sources: CAS from depositories and registrars, NPS, EPFO, Groww, Zerodha, Kuvera and INDmoney files.
- About 140 bundled brand logo files for banks, payment apps and merchants.

## Limits and known gaps

- Android only; no iOS or web version.
- A transaction with no SMS, email or notification is not seen until a later stated balance reveals the gap.
- Scanned (image-only) statements cannot be read.
- Outlook accounts need two-step verification before an app password can be created; Microsoft sign-in is not built.
- Layouts from banks without a template are read by the generic parser and may need a category correction.
- Notification reading depends on Android's notification access, which some phones restrict for apps installed from an APK (Allow restricted settings).
- Tax figures are an estimate from tracked data, not a filing.
- Goals, split and lend, credit score and manual tax inputs are not part of the app.
- Some listed holdings and tax details depend on what statements and messages contain; the app does not call bank, broker or tax-portal services.
