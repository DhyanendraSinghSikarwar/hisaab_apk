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
