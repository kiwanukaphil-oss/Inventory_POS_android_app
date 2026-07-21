# Inventory POS Android app

This workspace now contains both the native Android Phase 0 application and
the approved HTML UX prototype that preceded it.

## Native Android build

The Android app is built with Kotlin, Jetpack Compose, Material 3, and
Navigation 3. The implementation roadmap is in
[`docs/ANDROID_BUILD_PLAN.md`](docs/ANDROID_BUILD_PLAN.md).

Build and test on Windows:

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

Install the debug APK on a USB-connected Android device:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.kline.inventorypos.debug/com.kline.inventorypos.MainActivity
```

The debug package is `com.kline.inventorypos.debug`. Phases 1 and 2 include
secure login/session handling, authorized branch and register context, a
branch-scoped offline Room catalog, name/SKU/barcode search, permissionless
camera scanning through Google Code Scanner, real variant selection,
stock-guarded persistent carts, customer lookup, promotion evaluation, and
held-sale restore. The isolated demo workspace exercises the same persistence
and UI paths without requiring a backend.

Phase 3 adds validated checkout, cash/card/mobile/account payment allocation,
split tendering, duplicate-tap protection, durable ambiguous-attempt recovery,
server-confirmed receipts, Android system printing, and receipt email. The
roadmap documents the backend idempotency-key and composite-quote additions
required before financial staging acceptance.

Phase 4 adds branch inventory metrics, movements, adjustments, supplier GRNs,
saved receiving drafts, transfer lifecycle controls, approval-aware selling
price changes, price history, and auditable EAN-13 label batch printing through
the Android system print service.

Phase 5 now includes a branch-scoped Activity workspace for receipt search,
sale detail, attribution, reprinting, receipt email, and guided return/exchange
workflows with server-authoritative values and financial retry safeguards.
The More workspace now opens mobile customer accounts with contacts, purchase
history, balances, aging, statements, loyalty activity, and internal notes.
Gift vouchers can be issued as controlled drafts, activated after payment,
verified by code or camera scan, redeemed, cancelled, and cash-refunded with
drawer and uncertain-result safeguards.

Phase 6 includes a permission-aware cash workspace with daily movements and
totals, manual cash entries, blind drawer close, atomic handover, variance, and
the final server-authoritative Z-summary. The date-focused End of day workspace
adds daily sales and payment reporting, independent channel verification,
mandatory variance notes, self-only staff sign-off, and manager-controlled day
closure with explicit readiness checks.

Phase 6 also includes a branch-scoped Expenses ledger with period/category
filters and permission-gated create, edit, and delete workflows. Managers have
a dedicated approval queue for discount, cash, stock, price, and no-sale
exceptions, including self-approval prevention, mandatory rejection reasons,
full request evidence, and interrupted-decision safety locks.

Business documents now provide branch-scoped quote, invoice, and corporate
receipt search; touch-first manual document creation and draft editing; line
item totals; validated status transitions; quote-to-invoice and
invoice-to-receipt conversion; linked-document history; and permission-gated
voiding with a mandatory reason.

The administration overview is also available on mobile with permission-scoped
store identity, tax and returns policy, receipt/printer configuration, branch
status, and active-team visibility. Infrastructure-sensitive printer changes
remain read-only until the target hardware and connection mode are confirmed.

To connect a debug build to a backend running on this computer over USB:

```powershell
adb reverse tcp:5000 tcp:5000
.\gradlew.bat assembleDebug -PINVENTORY_POS_API_URL=http://127.0.0.1:5000/api/
```

Supply an HTTPS `INVENTORY_POS_API_URL` for release builds. Without one, the
release client intentionally targets the non-routable `https://api.invalid/`
placeholder.

## HTML UX prototype

This is a standalone, interactive HTML prototype for the Android version of
[Inventory POS](https://github.com/kiwanukaphil-oss/Inventory_POS). It uses mock
data and does not connect to the production API.

## Open the prototype

Open `index.html` directly in a browser, or serve the folder locally:

```powershell
python -m http.server 8080
```

Then visit `http://localhost:8080`.

On a desktop, the prototype is presented inside an Android phone frame with a
screen navigator. At tablet and phone widths it becomes a full-screen app.

## Included screens and interactions

- Dashboard with branch/register context, revenue, quick actions, alerts, and a
  sales pulse.
- Touch-first POS catalog with search, category filters, barcode entry point,
  product variants, stock visibility, and a persistent cart summary.
- Cart with customer attachment, quantity controls, hold, discount, tax, and
  promotion totals.
- Tendering with payment tiles, cash quick amounts, change calculation, and
  customer-dependent payment methods.
- Sale success with auto-print status, reprint, email receipt, and new-sale
  actions.
- Stock dashboard with low-stock priorities, receiving, transfers, and
  adjustments.
- Guided GRN/receive-stock entry with supplier reference and draft behavior.
- Sales activity for completed sales, returns, and receipt-detail entry.
- Secondary modules grouped under More: customers, vouchers, catalog, cash,
  documents, reports, settings, and team administration.

The primary checkout path is clickable:

`Sell → Add product → Cart → Payment → Receipt → New sale`

Individual screens can also be opened with `?screen=payment`, replacing
`payment` with `home`, `sell`, `cart`, `success`, `inventory`, `receive`,
`activity`, or `more`.

## Reference decisions carried into the mobile UX

- Deep teal primary and slate foundation from the web design tokens.
- Green is reserved for financially positive values; amber signals attention.
- Currency uses a tabular monospace stack.
- Branch scope, register state, user permissions, cash-drawer operations, held
  sales, approvals, customer credit/prepaid, and inventory workflows remain
  visible in the information architecture.
- Checkout follows the web app's accepted variant, payment-tile, natural split,
  and auto-print receipt direction.
- Dense web workspaces are adapted into focused mobile tasks instead of copied
  as tables or a sidebar.

## Prototype scope

This version is intended for visual and workflow review before Android
implementation. API integration, authentication, offline persistence, printer
and scanner hardware bindings, accessibility testing on devices, and final
permission enforcement belong to the implementation phase.
