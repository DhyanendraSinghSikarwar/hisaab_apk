# Changelog

All notable changes to DhanKosh (formerly Hisaab, briefly Artha) are listed here, newest first.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Dates are release dates.
Version numbers are not contiguous: some numbers were never published. 1.1.0 is the first versioned release.

For the current feature set, see [FEATURES.md](FEATURES.md).

## [2.9.8] - 2026-10-12

### Added
- Card page shows where its figures came from, for example "From statement · 2 Oct", "From SMS", "From email" or "Edited · 10 Oct".
- Tax centre: choose this FY, last FY or the last 12 months; income is collected from your data, or estimated for the full year when there are fewer than 12 months of data.

### Changed
- Tax centre: latest FY rules, new and old regime side by side with the recommended one, no tax up to ₹12 lakh in the new regime, surcharge with marginal relief, capital-gains rates and per-year rule tables.
- A manual edit of a card's billed, unbilled or total amount stays until a statement or SMS that arrives later replaces it; one that arrived earlier never overrides it.
- Net worth card dues now equal the sum of the amounts shown on each card, so balances and dues agree across Home, Accounts, Portfolio and the card page.

### Fixed
- A statement mail read by the app now reaches the right card even when the bank is spelled differently (for example "Axis" and "Axis Bank"), the card number is missing or masked, or the table separates labels with "|".
- Statements without a statement date, and locked or unreadable statements, no longer distort a card's due figures.

## [2.9.6] - 2026-10-11

### Changed
- Home credit card dues: the amount to pay is right-aligned on each card row.

## [2.9.5] - 2026-10-11

### Added
- Untracked payments: money that left or arrived without a message is detected from the next stated balance. The account page offers Add transaction, Assign to an existing notification row, or Ignore.
- Transfers between your own accounts and cards record both sides; a transfer to a credit card counts as a card payment.
- 49 more brand logos for transactions, including Croma, Shell, OYO, Zoom, GitHub and OpenAI.
- Support page: preset amounts of 100, 200, 500 and a custom amount.
- Axis statement SMS with total and minimum due updates the card's billed amount, minimum due and due date.

### Changed
- Credit card bill payments appear in Transactions but are never counted as spent or income, whether paid by SMS, UPI, NEFT, CRED or an app notification.
- Support page uses a cup icon.
- Language packs updated.

## [2.9.4] - 2026-10-11

### Added
- Support page: amount choices and a Pay with a UPI app button; the UPI ID is not shown.

## [2.9.3] - 2026-10-11

### Changed
- Card page simplified: one Edit amounts control and plain tiles.
- The account row on a transaction is tappable, with Remember for this payee.
- Language packs updated.

### Fixed
- A payment-app notification no longer creates an account; it waits for the bank message and adopts its account.
- The same payment seen as SMS, email, notification and card alert is one transaction, with the exact amount (paise kept) and the payee name.
- Two genuine payments of the same amount are no longer merged unless they are within 30 minutes of each other.
- Balances and card limits are applied once per payment, in either arrival order.
- Earlier duplicate app-notification rows are merged once.
- Promo and balance lines in notifications are no longer read as the amount.

## [2.9.2] - 2026-10-11

### Added
- Total outstanding field and headline on the card page; billed and unbilled amounts update it live.
- Edit amounts opens from every card tile and a full-width button, for cards with or without a statement.

### Changed
- Home card renamed Credit card dues.
- Only credit cards are listed; a card with no type counts only when its alerts show a credit limit.
- Language packs updated.

## [2.9.1] - 2026-10-11

### Added
- Edit amounts on the card page: billed, minimum due, due date and unbilled, with the calculated value shown and a reset. A newer statement or a full payment replaces them.

### Changed
- Home credit cards show one amount and one status line per card; Mark paid is a small check button.
- Spent this month follows the Period and Book filters; its title and comparison match the period.
- Transactions summary keeps the three coloured figures without the bar.
- Language packs updated.

### Fixed
- Removed a duplicate hide-amount eye inside the Spent card.

## [2.9.0] - 2026-10-10

### Added
- Spent this month card at the top of Home: total, change versus last month, running line, income, expenses, saved, and a foldable account and card list.
- Credit card due tracking: billed amount and pay-by date from statements, SMS and email, with an estimated bill when none arrives. Spends raise unbilled and lower limit left.
- Card bill reminders 5 days and 2 days before, and on the due date, with Mark paid and Remind tomorrow.
- Edit button on the card page.
- Payments filed as a loan can be linked to a loan; duplicate loans can be merged and unmerged.
- Holdings files read by column names for Groww, Zerodha, Kuvera, INDmoney and other apps, including numbers stored as text.
- Activity log reasons for payment-app notifications that could not be read, and a warning when notification access is off.

### Changed
- Net worth moves to Portfolio with the same card and 1M to All ranges; Safe to spend is removed from Home.
- Credit cards fold on Home.
- Account balances stay current from every SMS, notification and manual entry.
- Credit card bill payments from accounts are not counted twice.
- Transactions filters fit on one line; Income, Expenses and Net are coloured; the add button leaves Home.
- Release notes in the update screen list features.
- Language packs updated.

### Fixed
- Credit card spend alerts are read for every major issuer, including the newer Axis layout.
- Payment-app notifications (PhonePe, GPay, Paytm and others) are read in many more layouts.

## [2.8.0] - 2026-10-08

### Added
- Reversible account merge: a display grouping only, with Unmerge, and a Restore missing accounts action.
- Per-account statement unlock details (customer ID, account number, IFSC, name, mobile, email), stored sealed on the phone and inherited from the profile.
- Manual merge of two transactions, reversible with Split.
- About 55 more Indian banks (public, private, small finance, foreign, payments, co-operative) with sender codes and mail domains.

### Changed
- Card rows show the payment-network logo and a Business badge, with billed amount, due date and unbilled amount in place of limit left and spend counts.
- Automatic clean-ups no longer delete accounts that have transactions.
- Language packs updated.

### Fixed
- Generic phrases such as "View Details" no longer become merchants.
- App-notification amounts without paise now match the SMS duplicate.

## [2.7.0] - 2026-10-07

### Added
- Separate Credit cards card on Home with the billed amount to pay; card detail with limit, billed and unbilled.
- Customise setting for which money counts in net worth.
- Holdings statements from Groww and other platforms (Excel, CSV, email), summed per scheme and checked against the sheet totals.
- Add and remove loans; Not a loan for wrong detections.
- Support page (QR and UPI), What's new button and profile logo.

### Changed
- Net worth card in neutral grey; breakdown pins collapsible.
- Transactions: Type, Bank and Source dropdowns on one line; News without a date filter.
- Sync also re-reads the last 14 days of SMS.
- Language packs updated.

### Fixed
- Tata funds are no longer listed as lenders.

## [2.6.0] - 2026-10-07

### Added
- Renamed DhanKosh, with a shield logo as icon, splash and welcome mark; default DhanKosh palette and a Fold theme.
- Language packs for Hindi, Bengali, Telugu, Marathi, Tamil, Gujarati and Kannada, downloaded on demand.
- Activity log, merging of duplicate accounts and cards, and deleting accounts and cards.
- NBFC loans (for example Propelld and Aditya Birla Capital).
- NPS and Groww holdings; EPF, PPF, FD, RD and INDmoney values without double counting.
- Sync progress pill; Bank, Type and Source filters on Transactions; Book chip beside the period.
- Notification when a statement shows a different balance.
- Market news from several feeds with Today, Week and Month ranges.

### Changed
- Legend pins and billed/unbilled amounts on cards.
- Donut chart animates once; More list is never blank.

### Fixed
- Phone-number accounts are never created.

## [2.4.0] - 2026-10-07

### Added
- Renamed Artha, with a new launcher icon, splash and welcome mark.
- Loan tracker: EMI, paid, outstanding, terms and amortisation, with EMIs in Bills and a Loans page in More.
- Profile redesign with a photo-colour gradient and crop, zoom and rotate before saving.
- Text size setting; Hero, Cinema and soft palettes; pure-black dark mode.
- Language screen and download-on-demand language pack support.

### Changed
- Transaction rows show clean merchant names, time, category, sub-category and method, with colour-coded amounts.
- Tab spring, count-up and shimmer animations, and haptics.

## [2.2.0] - 2026-10-06

### Added
- Profile: names and mobile numbers as per bank, plus alternates, all used to unlock statements.
- Net worth bar split by account, with a legend; credit cards and limits listed separately and not counted.
- CAS detail with holdings, value and email; Excel and CSV statements; Groww-style fund and SIP emails update holdings.
- Tax centre what-if (not saved), shareable as an HD image or PDF.
- Six themes, press and spring animations.

### Changed
- Book (Personal, Business, All) is set once in More and applies app-wide; the Home accounts widget and edit pill are removed.
- Net worth history is estimated from transactions so every range works.
- Portfolio groups are collapsed by default; Review and Statements headers are pinned.

## [2.1.0] - 2026-10-06

### Added
- Manual rules by exact or contains match with category and sub-category; user rules win and can be applied to past transactions.
- Tax centre overrides for income and deductions per financial year, with reset to calculated.
- Layered background, soft glows, tinted card shadows and gradient hero cards.

### Changed
- Portfolio asset-class groups and maturity calendar collapse.
- Analysis opens on Spending; Budgets moved to the end.
- Data sources, Security & backup, Notifications & alerts and Customise each hold their own controls; Settings becomes About & updates.

## [2.0.0] - 2026-10-06

### Added
- Five-tab layout: Home, Transactions, Portfolio, Analysis, More.
- Global Book (Personal, Business, All) and Period filters shared by every tab.
- Home widgets with edit mode and presets; net worth history recorded daily.
- Transactions: type chips, In, Out and Net, a review banner, and List, Calendar and By merchant views.
- Portfolio: value, allocation, net worth range chart, holdings by class and a maturity calendar.
- Analysis: Spending (cross-filter, monthly bars, heatmap, merchants), Cash flow, Forecast and Compare.
- More: tax centre, business book, rules and data sources.

### Changed
- Redesigned calm palette with flat cards and an icon-only bottom bar.

### Removed
- Goals, split and lend, credit score and manual tax inputs; only features fed by SMS, email, statements and notifications remain.

## [1.13.0] - 2026-10-06

### Added
- Forex rates page and per-card markup; totals use the rupee value.
- Profile asks for date of birth and PAN; locked statements are tried against passwords built from them.
- FD and RD maturity date and instruction.
- Add cards and accounts by hand.
- Statement mails picked by subject (CAS, NPS, EPF); Outlook app-password steps.

### Fixed
- Balance-only alerts are no longer read as credits.
- Card merchants such as UBERIND are recognised.
- The payee's account is never taken as the user's own.

## [1.12.0] - 2026-10-05

### Added
- Customise tabs: show, hide and reorder sections.
- Locked statements show the email they came with as a password hint; unlock passwords are remembered and retried on every locked and new PDF.
- Account detail page with spend and income lines over 6 months, a year or all time.
- Portfolio allocation bar and a statements icon with a red dot when one is locked.

### Changed
- Analytics: KPIs first, one-row filters with a sheet.
- Home month filter applies to every section.
- Bills & insurance shows only the calendar; items are editable from it.

## [1.11.0] - 2026-10-05

### Added
- First-run sign-in with a verified email or phone that creates the profile.
- Bills & insurance month calendar with marks per kind.
- Accounts, Cards and Deposits & loans tabs with FD, RD, PPF and loan tags.
- Notification bell and savings line chart on Home.
- Nightly email sync and nightly SMS catch-up.

### Changed
- Bottom bar uses icons only; Settings moved to the profile page.
- App lock keeps the current screen; solid top bars.

## [1.10.0] - 2026-10-05

### Added
- Profile (name, photo, email, phone, occupation), with a first-run prompt.
- Sub-categories with icons, auto-assigned for known merchants; custom categories and sub-categories with an icon picker.
- Email view with sender, subject and readable body.
- Statements read opening and closing balance, debits, credits and available limit, update the account, and can be read again.
- App-password guide per provider with direct links.

### Changed
- Home shows categories only and drills down by sub-category.

## [1.9.0] - 2026-10-05

### Added
- Redesigned Home: greeting header, spend hero, budgets, recent transactions, accounts carousel, subscriptions and activity heatmap.
- Real logos for 91 banks, payment apps and merchants, bundled offline.
- Analytics filters: this month, last month, this FY, all time and custom range; personal or business scope; accounts or cards.
- Accounts can be marked Personal or Business.
- Manual recurring items, income or expense, monthly or yearly.
- Search bar with match count in Transactions.

### Changed
- Own emerald, sapphire and gold palette with a gradient backdrop; wallpaper colours are no longer applied.
- Amounts are hidden by default.

## [1.7.0] - 2026-10-05

### Added
- Statement reading: card, bank and CAS or broker PDFs from email or the phone, with saved passwords tried on each.
- Statements update card available limit and bank closing balance.
- Investments tab: EPF and NPS from SMS, mutual funds and stocks from CAS.
- Card spends from all issuers; debit cards linked to accounts; UPI from payment-app notifications; screenshot import; remembered merchant categories.
- Multiple email inboxes.
- Monthly budgets with threshold alerts; recurring payments with low-balance alerts 3 days ahead; insurance renewals; insights.
- New-transaction notifications with category buttons.
- In-app updates from GitHub Releases, verified by SHA-256.

### Changed
- Self transfers and card bill payments are counted as transfers.
- Interactive charts, motion, grouped category sheet, hide amounts and a floating bottom bar.

## [1.1.0] - 2026-09-30

### Added
- Card SMS formats such as "Transaction of", "Txn Rs" and "has been used for".
- Email by address and app password over IMAP, verified by a 6-digit code; the password is sealed with an Android Keystore key. Google sign-in kept as an advanced option.
- One Fetch history window (7 to 365 days) for SMS scans and email sync.
- Home spent and income per calendar month, with month navigation.
- Optional user-set account balance, carried forward by later transactions and replaced by a newer bank-reported balance.

### Fixed
- Bank and card-issuer SMS were being dropped by the pre-filter.
- OTP messages of the form "OTP ... is 123456" are now rejected.
- The SMS permission card re-checks on resume and opens App settings when Android will not prompt.
