# Spec notes (from Hisaab_Product_Spec.md, Oct 2026)

Condensed reference. The full spec was shared by the user; this keeps what applies here.
**Scope rule:** build only what SMS, email, mailed statements or app notifications can fill.
Out of scope: goals, split & lend, credit score, manual assets, market prices, Account Aggregator.

## Principles
Zero manual entry · one transaction recorded once (SMS + email + statement merge) · on-device only ·
every total drills down to transactions → evidence (original message) · Personal / Business books.

## Money rules
- Transfers between own accounts and card bill payments are never spend.
- Investment debits (SIP) count as Invested, not Spent. Refunds reduce their category, not income.
- Saved = Income − Spent − Invested. Savings rate = (Income − Spent) ÷ Income.
- Indian format: ₹1,23,456 · ₹12.3 L · ₹1.2 Cr. FY = Apr–Mar.

## Navigation
Tabs: Home · Transactions · Portfolio · Analysis · More (icons only).
Global chips: Book (Personal / Business / All) and Period (week, month, last month, this FY, last FY,
last 12 months, custom) apply app-wide. 👁 masks every amount.

## Tabs
- Home widgets: net worth, cash flow, safe-to-spend, upcoming 7 days, insights, category donut,
  accounts strip, recent, budget rings. ✎ Edit: reorder, hide, presets.
- Transactions: search, type chips, In/Out/Net, review banner, List / Calendar / By merchant.
- Portfolio: value, allocation donut, net worth over time, holdings by class, maturity calendar.
- Analysis: Spending (donut cross-filters the page, 12-month stacked bars, weekday×time heatmap,
  top merchants) · Cash flow (Sankey, savings rate) · Forecast (month-end fan, 30-day cash) · Compare.
- More: budgets, bills, tax centre, business book, rules, data sources, customise, security, settings.

## Statement passwords
Try saved passwords, then patterns from name, DOB, PAN, mobile, card last 4 (NAME4+DDMM, DDMMYYYY,
PAN for CAS). Ask only if all fail.

## Design tokens
bg #F7F7F5 / #0E0F12 · surface #FFF / #17191E · surface-2 #F0F0EC / #20232A · border #E6E6E1 / #2A2D35 ·
accent #2F5BEA / #7C9BFF · pos #13895A / #3CCB8B · neg #D2453B / #FF7B70 · warn #C98A0B / #F2B84B ·
transfer #7A808A / #8B919C. Category palette: 2F5BEA E07A2E 16A394 C2418B 7A5AE0 C9A227 3E9BD6 8C8F96.
Cards: 16dp radius, 16dp padding, 1dp border, no shadows. Tabular numerals. Motion 200–400 ms.

## Ideas not built yet (tracked-data only)
Bank-charges "leakage" insight · card utilisation >30% insight · EPF missing-month insight ·
statement balance reconciliation tick · commerce enrichment (order email ↔ UPI debit) · FTS search.
