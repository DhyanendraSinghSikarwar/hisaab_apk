# Hisaab plan

Short log of requests. One line per item. `[x]` done, `[ ]` open, `[~]` partial (note why).
Done releases are collapsed to one line; details live in git history.

## Rules
- Data stays on the phone. Nothing uploaded.
- UI: professional, concise, aesthetic. Commit/push only when asked.
- Agent guide: `AGENTS.md`. Wiki source: `doc/wiki/`.

## Released
- 1.9.0 search, analytics filters, personal/business accounts, gradient theme, brand logos
- 1.10.0 profile, sub-categories, custom categories + icons, email view, statement totals
- 1.11.0 sign-in, account/card/deposit tabs, icon-only bar, bills calendar, nightly sync
- 1.12.0 statement email hints, auto-try passwords, account charts, customizable tabs

## 1.13.0 (requested 2026-10-06)
- [x] Balance-only alerts (HDFC "available balance … is Rs") are not credits
- [x] Card SMS "At UBERIND13513699" → Uber, Transport › Cabs
- [x] Transfers: payee's account is not added as the user's account
- [~] Outlook: guide says turn on two-step verification first (App passwords hidden until then). Microsoft sign-in (OAuth) not built: needs an Azure app registration
- [x] Profile asks full name, mobile, DOB, PAN (sign-up + Profile); statement passwords guessed from them
- [x] Travel › Cabs sub-category (Uber, Ola, Rapido, BluSmart, Meru, Namma Yatri)
- [x] FD/RD: maturity date + instruction; paid-out deposits hidden on maturity, renewed stay
- [x] Add a card/account manually (Accounts › +)
- [x] Forex: Settings › Forex rates (rate per date, nearest date used); totals use `inrMinor`
- [x] Card edit: forex markup %
- [x] Statement mails picked by subject too (CAS via INDmoney, NPS, EPF); more CDSL/NPS/EPFO domains
- [x] AGENTS.md and doc/PLAN.md
- [~] GitHub wiki: pages in doc/wiki/; waiting for the wiki to be enabled on github.com, then push to hisaab_apk.wiki.git
- [x] Transaction detail: rupee value for forex, no parser internals

## 2.0.0 redesign from Hisaab_UI_Prototype.html (requested 2026-10-06)
Inspiration, not a copy. Keep settings, profile and every existing feature. Bottom bar: icons only.
- [x] Palette + background from the prototype (light/dark), flat surfaces, animations
- [x] Home: keep profile, accounts, notifications, refresh, add; eye mask; no gear. Widgets: net worth, cash flow, safe to spend, upcoming, insights, category donut (drill-down), accounts, recent, budget rings; edit mode with presets
- [x] Transactions: keep bills top right; search, type chips, in/out/net, needs-review banner, List / Calendar / By merchant; new icon
- [x] Portfolio: keep statements on top; value + gain, allocation donut, net worth chart (1M–All), holdings by class, maturity calendar; new icon
- [x] Analysis: Spending (donut cross-filter, 12-month bars + avg, when-you-spend heatmap, top merchants), Cash flow (sankey, savings rate), Forecast (month-end fan, cash 30 days), Compare
- [x] Global book (Personal/Business/All) + period chips
- [x] More tab: Budgets, Bills, Tax centre (from tracked data), Business book, Rules, Data sources, Customise, Accounts, Statements, Security & backup, Settings, Profile
- Rule (2026-10-06): only features fed by SMS, email, mailed statements or app notifications. Dropped: goals, split & lend, credit score, manual tax inputs
- [x] Portfolio tab (agent): value, allocation, net worth, holdings by class, maturity calendar
- Built + unit tests pass; not yet run on a device

## 2.1.0 (requested 2026-10-06)
- [x] Stylish, appealing background and richer (still professional) styling
- [x] Tax centre: pencil (top right) to override values; default stays calculated
- [x] Portfolio: asset-class groups and maturity calendar expand/collapse
- [x] Rules: add rules manually (category + sub-category); user rules win over automatic
- [x] Analysis opens on Spending; Budgets section at the end
- [x] More: each item edits only its own function (Security & backup, Data sources, Customise…); removed from Settings, one place each
- Built + all tests pass (DB v13); not yet run on a device

## 2.2.0 (requested 2026-10-06)
- [x] Profile: "as per bank" names; alternate name + alternate mobile; all used to unlock statements (DOB, PAN, phones, names)
- [x] Profile gear → payment-app notifications, history to read (Data sources)
- [x] Home: remove Accounts widget
- [x] Book (Personal/Business/All) only in More (replaces Business book); filters accounts, cards, portfolio, transactions app-wide
- [x] Net worth: bar split by account; credit cards + limits listed below (not counted)
- [x] CAS statement detail shows content, holdings and amounts (was ₹0 / ₹0)
- [x] Read Excel (xls/xlsx) and CSV statement attachments
- [x] Groww-style MF/SIP emails → holdings (units, invested, total value)
- [x] Tax centre: edits are what-if only (not saved); share calculation as HD image or PDF
- [x] More: no edit button top right
- [x] Portfolio: net worth range works (history estimated from transactions); bag icon; groups collapsed by default
- [x] Review (duplicates) and Statements: content must not slide under the tabs
- [x] Professional animations (press, stretch overscroll, springs)
- [x] 4–5 extra professional themes
- [x] Icons: one rounded-square shape; brands without a logo use their own two-colour gradient
- Home Edit pill removed (reading of "edit button top right"); widget layout stays in More › Customise
- Built + all tests pass; not yet run on a device

## 2.3.0 (requested 2026-10-06)
- [x] Home net worth: credit cards collapsible (collapsed by default); drop "not counted in net worth" and "available"
- [x] Bogus accounts ••6810 (user's mobile) at Axis/SBI: parser must not take a mobile number/VPA as account; clean existing
- [x] NEFT/IMPS to someone else: their account not added as mine, not counted as income; clean existing
- [x] Tax centre: no footer text; important notes behind ⓘ buttons
- [x] Samsung Wallet/Pay and WhatsApp Pay notifications
- [x] Analysis › Spending: "When you spend" last
- [x] Statements (and other pushed screens) clear of the system navigation bar
- [x] Portfolio: news icon (replaces statements) → Market news page (Moneycontrol RSS: Markets, Latest, Business, Stocks); tap opens browser
- [x] Profile: no settings gear; Data sources: no Statements section
- Built + all tests pass; one-time clean-up of wrong accounts runs on first launch; not yet run on a device

## 2.4.0 (requested 2026-10-06)
- [x] Profile: aesthetic redesign; crop/adjust photo before saving; gradient background from the photo's colours
- [x] Security & backup: text size options (must fit on screen)
- [x] More › Language: approach confirmed (download on demand, 7 languages)
- [x] Text size (Security & backup): Small / Default / Large / XL, capped so layouts fit
- [~] Language: More › Language; English built in; Hindi, Bengali, Telugu, Marathi, Tamil, Gujarati, Kannada download on demand from repo lang/<code>.json, delete anytime. Infra done; text extraction + translations pending
- [x] Loan tracker: loan detail (EMI, paid, left, payoff), loan terms, EMIs in bills, More › Loans (DB v14)
- Rule: everything aesthetic and professional
- [x] Profile: photo-colour gradient hero, crop/zoom/rotate before saving, cleaner cards
- [x] Hero-inspired themes (neutral names): Midnight Knight, Arc Red, Star Shield, Thunder, Gamma, Vibranium; pure-black dark mode toggle
- [x] Micro-animations: tab spring + sliding indicator, count-up hero figures, staggered cards, shimmer, haptic ticks
- [x] Film-inspired themes (Cinema group): Iron Throne, Middle Realm, Nitro, Neo Matrix, Interstellar Dust, Spice Dune
- [x] Transaction rows: clean merchant (no Pay*/Raz*), time · category › sub-category · method; amounts coloured (income green, card amber, bank/UPI red, investment accent, transfer grey)
- [x] Two soft palettes: Rose Quartz, Lavender Bloom
- [x] Loan tracker (DB v14): loan detail, terms + amortisation, EMIs in Bills, More › Loans, Portfolio loans list
- [x] Renamed to Artha (label, all visible text, welcome screen, release title/APK name); new logo as launcher icon and splash. Package id stays com.hisaab.app so updates keep data
- [x] Themes: Rose Quartz, Lavender Bloom; Hero and Cinema groups; pure-black toggle; tab spring, count-up, shimmer, haptics
- 2.4.0 built + all tests pass (DB v14); language text extraction and translations next

## 2.5.0 (requested 2026-10-07)
- [x] Language packs: 7 languages translated (lang/<code>.json), pushed so download works; help text removed
- [x] Transaction rows: time · category › sub-category only; method/sources on detail
- [x] Delete account/card (confirm; optional delete of its transactions)
- [x] Never create accounts numbered like the user's mobile (••6810); remove existing at start
- [x] Loans from NBFC lenders (Propelld, Aditya Birla Capital / ABCD …) detected into Deposits & loans
- [x] Groww / MF holdings: invested, gain, value from statements and emails
- [x] NPS from SMS and emails into Portfolio

## 2.6.0 (requested 2026-10-07)
- [x] Book (Personal/Business) chip back at the front beside the period chip; removed from More
- [x] Home first (hero) card: keep it important but closer to the other cards
- [x] Net worth bar: legend pins below the bar
- [x] Credit cards: billed / unbilled amounts
- [x] Transactions filters: bank (logos) + type (All, Income, Spends, Investments) + source (SMS, Email, Statement, App); drop Needs review chip
- [x] Activity log: messages read, emails read, statements, last syncs, errors (More › Activity log)
- [x] Merge duplicate accounts/cards (edit sheet "Merge into…", duplicate suggestions on Accounts)
- [x] Tests: SMS / email / statement / fund flows end to end
- [x] Home sync: progress bar with messages and mails read
- [x] Market news: Today / Week / Month filter, newest first, current news (ET, Business Standard, Mint merged with Moneycontrol)
- [x] Allocation donut animates once per tab open
- [x] Portfolio values correct for INDmoney, Groww, NPS, EPF, PPF, RD, FD statements/messages; balance updates with a short "missing transactions" notification
- [x] More tab: no blank first scroll; clear end of list
- [x] Fold-style theme (minimal monochrome); second after DhanKosh
- [x] Loans from Propelld, Aditya Birla Capital (ABCD) and other NBFC lenders appear in Deposits & loans with their EMIs
- [x] New shield logo (transparent surround) as icon, splash, welcome mark
- [x] DhanKosh palette from the logo (green → teal → blue) as default; Fold kept as option
- [x] Lender loans: Propelld, ABCD and 15+ NBFCs → loan accounts with EMIs (tests added)
- [x] Groww MF holdings (PDF/XLSX) and CAS carry invested + current value, so gains show
- [x] NPS from CRA SMS/emails/Statement of Transaction: value, contributions as invested, PRAN ••1234 · Tier I in Portfolio
- [x] Renamed to DhanKosh (label, all text, language packs, release title/APK); default palette named DhanKosh
- 2.5.0 and 2.6.0 shipped together as 2.6.0: built, all tests pass (DB v14), not yet run on a device

## 2.7.0 (requested 2026-10-07)
- [x] Home sync button also re-reads the last 14 days of SMS (not just mail)
- [x] News: no Today/Week/Month filter
- [x] Holdings statements from Groww/other platforms (Excel/CSV upload or email attachment): same scheme in several folios summed; check against the sheet's totals
- [x] Icon: transparent around the shield (no grey/white tile)
- [x] Home net worth card: neutral grey surface
- [x] Transactions: no Book chip; the three filters on one line (Type ▾ Bank ▾ Source ▾)
- [x] Portfolio sections collapse faster
- [x] Home: separate Credit cards card (billed amount to pay in red) → card detail (limit, left, billed, unbilled); removed from net worth card
- [x] Customise: choose which money counts in net worth
- [x] What's-new ⓘ for updates (More › About & updates; bundled notes + release body)
- [x] Profile: app logo; Support (donate) icon → in-app page with QR / UPI id (details pending from user) - fill SupportConfig
- [x] Net worth pins collapsible, hidden by default
- [x] Tata investment shown as loan: fix detection; Portfolio loans section styled like Equity etc.
- [x] Loans: add / remove loans
- [x] Holdings sheets (Groww/INDmoney/Coin/ET Money/broker CSV, xlsx/csv/email): per-scheme folio sums, summary totals check, GOLD kind, 'Holdings statement' label
- Built + all tests pass (DB v14), not yet run on a device. Pending from user: UPI ID or QR image for the Support page (SupportConfig.UPI_ID / assets/support_qr.webp)
