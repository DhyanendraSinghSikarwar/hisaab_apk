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
- [~] GitHub wiki: pages in doc/wiki/; publishing needs the wiki enabled + first page created on github.com
- [x] Transaction detail: rupee value for forex, no parser internals
