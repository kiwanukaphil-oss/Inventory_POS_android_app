# Inventory POS Android — Build Plan and Product Roadmap

**Document status:** Active implementation roadmap
**Created:** 21 July 2026
**Reference application:** `kiwanukaphil-oss/Inventory_POS`
**UX baseline:** `index.html` in this workspace
**Initial test device:** Samsung SM-S926U, Android 14 / API 34

## Implementation status — 21 July 2026

- Phase 0 complete: native Compose shell, design system, core POS/stock flows,
  unit tests, lint, debug APK, and physical-device checkout verification.
- Phase 1 client complete: real backend contracts for login, current user,
  assigned branches, active drawer, and drawer opening; Android
  Keystore-encrypted token persistence; branch header injection;
  permission-aware navigation; session restore, branch switching, and logout.
- Phase 1 includes a local demo workspace for device and stakeholder review.
- Remaining Phase 1 release gate: validate live authentication and drawer
  operations against the nominated staging URL and staging accounts.
- Phase 2 client complete: branch-scoped Room catalog, offline search, Google
  Code Scanner, real variant selection, stock-guarded process-safe cart,
  customer lookup, server promotion evaluation, and local/server held sales.
- Remaining Phase 2 release gate: validate catalog, promotion, customer, and
  held-sale responses against the nominated staging environment and run the
  production-size weak-connectivity acceptance script.
- Phase 3 Android client complete: cart validation, tax/promotion/discount quote,
  touch-first saved and manual discount selection, server-authoritative preset
  IDs, manual discount amounts, approval-aware sale submission,
  one-entry-per-method split tendering, cash change, customer-gated account
  methods, durable checkout attempts, approval/uncertain outcomes, canonical
  receipts, system printing, email delivery, and confirmed-sale recovery.
- Remaining Phase 3 release gates: staging reconciliation and backend support
  for a composite quote endpoint plus enforced sale idempotency keys. Until
  then the client blocks automatic retry after an ambiguous network outcome.
- Phase 4 Android client complete: branch inventory, movements, adjustments,
  supplier receiving and GRNs, drafts, transfers, approval-aware price changes,
  price history, and audited EAN-13 label batch printing.
- Remaining Phase 4 release gate: reconcile inventory and pricing mutations and
  verify label output against the nominated staging backend and pilot printer.

## 1. Product objective

Build a secure, reliable, touch-first Android client for Inventory POS that
preserves the web system's business rules while making the daily cashier and
stock workflows fast on a handheld device.

The Android app is a client of the existing Inventory POS backend. It will not
fork tax, pricing, discount, stock, payment, approval, or accounting rules into
an independent mobile-only authority. The server remains authoritative for
committed financial and inventory records.

## 2. Success criteria

The first production pilot is successful when staff can:

1. Sign in, select an authorized branch, and open or resume a register session.
2. Search or scan products, resolve variants, see branch stock, and create a
   cart without noticeable input lag.
3. Attach a customer and correctly expose loyalty, prepaid, credit, promotion,
   and approval-dependent options.
4. Complete supported payments, receive a server-confirmed sale, and print,
   reprint, or send the receipt without duplicating the transaction.
5. Receive, adjust, and transfer stock with branch scope and auditable reasons.
6. Find recent sales and perform permission-gated return/exchange workflows.
7. Recover safely from process death, weak connectivity, rotation, and printer
   failure without losing or duplicating committed work.

## 3. Scope and priority

### P0 — Pilot-critical

- Authentication and secure session handling
- Branch selection and branch-scoped cache invalidation
- Cash drawer open/resume, cash movements, handover, and close
- Product search, barcode scan, variant selection, branch stock
- Cart, customer attachment, discounts/promotions, held transactions
- Cash, card, and mobile-money checkout
- Split-ready payment model with one entry per method
- Receipt view, automatic print attempt, reprint, and email
- Sales activity and sale detail
- Low-stock dashboard, stock adjustment, stock receiving/GRN
- Permission-aware navigation and actions
- Structured error handling, telemetry hooks, and audit-safe logs

### P1 — Operational completeness

- Returns and exchanges
- Customer detail, loyalty, prepaid, credit, and payment terms
- Gift voucher scan, issue, redemption, cancellation, and refund paths
- Branch stock transfers
- Price management and promotions
- Suppliers and GRN history
- Approvals inbox
- Cash book, expenses, and daily reconciliation/Z report
- Label printing

### P2 — Management depth

- Business documents
- Reports and exports optimized for mobile summaries
- Product creation/editing and image capture
- User, role, branch, tax, printer, and store administration
- Import/export administration where it is safe and useful on mobile

## 4. Mobile information architecture

The primary navigation stays intentionally small:

| Destination | Purpose |
|---|---|
| Home | Live branch/register overview, alerts, and quick actions |
| Activity | Sales, held transactions, returns, and receipt lookup |
| Sell | Product scan/search, variants, cart, and checkout |
| Stock | Stock status, receiving, transfer, adjustment, and movements |
| More | Customers, vouchers, cash, documents, reports, and administration |

Checkout, stock receiving, cash close, returns, and similar workflows use
focused full-screen task flows. They do not retain bottom navigation while a
transaction is in progress.

## 5. Technical baseline

### Platform

- Kotlin 2.3.21
- Jetpack Compose with the stable Compose BOM
- Material 3 with a custom Inventory POS design system
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- JDK 17
- `compileSdk` and `targetSdk`: 36
- `minSdk`: 26 (Android 8.0)
- Initial application ID: `com.kline.inventorypos`

The application ID is a release identity. It must be confirmed before any
signed pilot build is distributed because changing it later creates a separate
installed application.

### Architecture

- Single-activity Compose application
- Navigation 3 stable for Compose-first, state-owned navigation
- Unidirectional data flow: user action → ViewModel → state → UI
- Screen-level ViewModels exposing immutable `StateFlow` UI state
- Repository boundaries between UI/domain code and data sources
- Domain use cases for reused or financially sensitive workflows
- Kotlin coroutines and Flow for asynchronous work
- Room as the observable local source for cached structured data
- DataStore for non-sensitive preferences and selected operational context
- Android Keystore-backed protection for authentication secrets
- WorkManager for constrained, retryable background synchronization
- Dependency injection introduced at the data boundary before API integration

### Initial source layout

The first vertical slice starts as one Android application module with strict
package boundaries:

```text
app/
  core/common
  core/designsystem
  core/model
  core/network
  core/database
  core/data
  domain
  feature/auth
  feature/home
  feature/pos
  feature/inventory
  feature/activity
  feature/more
```

Feature and core packages will be extracted into Gradle modules when the first
network-backed vertical slice is stable. This keeps the first install fast
without allowing UI, network, and persistence concerns to become entangled.

## 6. Data and synchronization policy

### Server-authoritative records

The backend remains authoritative for:

- Sale identifiers and committed payments
- Tax, promotion, discount, and approval outcomes
- Stock balances and posted movements
- Customer monetary balances, credit, prepaid, loyalty, and vouchers
- Cash drawer totals, reconciliation, returns, and exchanges

Money is represented in integer minor/base currency units according to the
existing API contract; binary floating-point values are not used for business
calculations.

### Offline behavior

The first release is **offline-readable, online-commit**:

- Cached products, variants, categories, prices, and last-known stock remain
  searchable without a network connection.
- Carts, form drafts, and held-sale drafts survive process death locally.
- Committed sales, payments, stock postings, voucher redemption, and customer
  monetary balance changes require a server response.
- The UI always distinguishes fresh, stale, syncing, failed, and offline data.

Fully offline sales are deferred until the backend has an explicit idempotency,
sequence-allocation, conflict-resolution, and reconciliation contract. A
retrying client must never create duplicate financial transactions.

### Network contract

- Retrofit/OkHttp-compatible HTTP client behind repository interfaces
- Existing bearer/session authentication contract mapped centrally
- Consistent API envelope and domain-error translation
- Idempotency keys for retryable writes once supported by the backend
- Branch ID and register/session context applied centrally, not per screen
- Request/response logging disabled for credentials and personal/financial data
- Timeouts, retry policy, and cancellation tuned per operation type

## 7. Hardware integration plan

### Barcode input

1. Camera scan using CameraX and on-device barcode recognition.
2. Keyboard-wedge/USB/Bluetooth scanner support through rapid key input.
3. Manual barcode/SKU search fallback.

Scanner events route through one product-resolution service so all input modes
use the same variant, stock, and permission checks.

### Printing

1. Reuse the current print-bridge receipt and label contracts where reachable.
2. Provide Android system print/share fallback.
3. Add direct Bluetooth/network ESC/POS support only after target printer
   models, code pages, cash-drawer pulse behavior, and paper widths are tested.

A print failure never rolls back a sale that the server has already committed.
The app stores the confirmed sale reference and exposes a clear reprint action.

## 8. Security and privacy baseline

- No API credentials or signing secrets committed to source control
- Release secrets supplied through protected build/CI configuration
- Authentication secrets protected with Android Keystore-backed storage
- Clear session data on logout and remote-expiry response
- Permission checks in both navigation and the action surface; server remains
  the final authorization authority
- Sensitive screens excluded from recent-app snapshots where appropriate
- Personal and financial values removed from analytics and diagnostic logs
- Network security configuration blocks cleartext production traffic
- Release builds use shrinking/obfuscation with tested keep rules
- Exported Android components disabled unless explicitly required
- Dependency and SDK review before each release candidate

## 9. Quality strategy

### Automated tests

- Unit tests for money, totals, tender allocation, change, variant resolution,
  permission policy, and error mapping
- ViewModel tests for every screen state and user action
- Repository tests using fakes and deterministic dispatchers
- Room migration and DAO tests
- API contract tests against recorded fixtures and a staging backend
- Compose UI tests for checkout, receiving, return, and register flows
- Navigation regression tests, including Android predictive back
- Screenshot/golden tests for high-value responsive states

### Device verification

- USB smoke test on Samsung SM-S926U / Android 14 after every vertical slice
- At least one smaller handset and one tablet before pilot
- Dark mode, large font, TalkBack, switch access, and landscape checks
- Camera, scanner, printer, low-memory, airplane-mode, and interrupted-payment
  scenarios on real hardware

### Performance targets

- Warm screen transitions should feel immediate and avoid blocking the UI thread
- Product search and cart mutation remain responsive with a production-size
  catalog
- Cold-start and frame timing are measured in release-like builds
- Images are resized, cached, and decoded to display size
- Baseline profiles are added before pilot

## 10. Delivery roadmap

The roadmap is organized by exit gates rather than fixed calendar promises.
Each phase ends with a build installed on a physical phone.

### Phase 0 — Foundation and runnable shell

**Deliverables**

- Gradle project, version catalog, build variants, lint, and test foundation
- Compose design tokens matching the approved HTML mockup
- Responsive app shell and five-destination navigation
- Local sample data for Dashboard, Sell, Cart, Payment, Receipt, Stock,
  Receive Stock, Activity, and More
- First debug APK installed on the connected Samsung phone

**Exit gate:** clean debug build, passing unit tests, no startup crash, primary
prototype navigation usable on the USB-connected device.

### Phase 1 — Authentication, branch, and register context

**Deliverables**

- Backend environment configuration
- Login, logout, expiry, and secure token handling
- Authorized branch selection and default branch
- Permission model and permission-aware destinations/actions
- Register open/resume prompt and session status
- Central domain-error presentation

**Exit gate:** a real user can authenticate against staging and reach only
authorized, correctly branch-scoped screens.

### Phase 2 — Catalog and cart vertical slice

**Implementation status:** Client complete on 21 July 2026; staging contract
acceptance remains open.

**Deliverables**

- Product/category/variant network and Room cache
- Text, SKU, barcode, and camera search
- Stock-aware progressive variant selection
- Cart totals, quantities, tax preview, promotions, and customer attachment
- Held transaction persistence and restore
- Catalog sync/staleness indicators

**Exit gate:** staff can build and recover a valid production-size cart with
weak connectivity and without duplicated line items.

### Phase 3 — Checkout, payments, and receipts

**Implementation status:** Android client complete on 21 July 2026; staging
reconciliation and the two server contract additions below remain open.

**Deliverables**

- Server cart preview before commit
- Payment tiles and one-entry-per-method allocation
- Cash, card, mobile money, split, customer-dependent methods, and references
- Discount approval handling and oversell soft block
- Idempotent submission boundary and duplicate-tap protection
- Confirmation, automatic print attempt, reprint, email, and new sale

**Exit gate:** scripted financial invariants pass and test sales reconcile with
the web application and backend.

**Backend contract blockers discovered during implementation**

- `POST /api/sales` does not currently enforce an idempotency key. Android
  sends `X-Idempotency-Key`, persists the attempt before submission, disables
  duplicate taps, and treats transport failure as uncertain, but only server
  enforcement can guarantee no duplicate after a lost response.
- The backend exposes cart validation, tax preview, and promotion evaluation
  separately, while its internal composite sale quote is not routed. Android
  reproduces the published calculation pipeline for display and the commit
  remains authoritative; a public composite quote is required to eliminate
  preview drift completely.

### Phase 4 — Inventory operations

**Implementation status:** Android client complete on 21 July 2026:
branch-scoped summary metrics, low/all-stock search, barcode lookup, movement
history, permission-aware approval-safe adjustments, and a three-step supplier,
items, review and posting workflow for purchase receipts/automatic GRNs. This
now also includes receive-draft save/resume/discard, expandable GRN history,
and the requested/dispatch/receive/cancel branch-transfer lifecycle with
source/destination safeguards. The final slice adds permission-aware,
approval-safe selling-price management with price history, plus selectable
EAN-13 label batches through Android system printing and backend print-run
audit recording.

**Deliverables**

- Stock dashboard, branch stock, low-stock and movement history
- GRN drafts, receiving review, cost/margin checks, and posting
- Adjustments with reasons and approvals
- Branch transfer lifecycle
- Price management, suppliers, labels, and GRN history

**Exit gate:** inventory movements created on Android match backend balances,
audit records, and the corresponding web views.

### Phase 5 — Sales aftercare and customer accounts

**Implementation status:** sales aftercare slices complete on 21 July 2026:
branch-scoped sales search, completed/return filters, canonical receipt detail,
cashier and salesperson attribution, Android reprint, safe email resend, and
permission-gated return/exchange workflows. Returns capture quantities,
condition, reason, refund/store-credit resolution, and drawer/customer gates.
Exchanges capture returned and replacement items, use the server preview as the
authoritative value difference, and require a valid settlement before commit.
Ambiguous network failures are blocked from blind retry and must be verified in
Activity. Customer account workspaces remain in the next Phase 5 slice.

**Deliverables**

- Sales search/detail, resend/reprint, and staff attribution
- Return and exchange workflows
- Customer workspace, contacts, notes, statements, and purchase history
- Loyalty, prepaid, credit limits, terms, payments, and balances
- Gift voucher issue, QR verify/redeem, refund, and cancellation

**Exit gate:** return/exchange/customer monetary invariants pass against staging
and every mutation is permission- and branch-correct.

### Phase 6 — Cash, management, and administration

**Deliverables**

- Cash movement, handover, close, reconciliation, and Z report
- Expenses, approvals, reports, and business documents
- Product maintenance and administration screens appropriate for mobile
- Branch, user, role, tax, store, and printer settings

**Exit gate:** managers can complete an end-to-end day from drawer open through
reconciliation with matching web totals.

### Phase 7 — Production hardening and pilot

**Deliverables**

- Accessibility and adaptive-layout audit
- Release performance profiling and baseline profiles
- Security review, dependency audit, backup/restore and migration exercises
- Crash/ANR monitoring and privacy-safe operational telemetry
- Signed internal release, pilot playbook, rollback plan, and staff training
- Play distribution or managed-enterprise distribution decision

**Exit gate:** pilot acceptance checklist signed, no unresolved critical defects,
and rollback/recovery tested.

## 11. Build environments and release flow

| Variant | Backend | Logging | Signing | Purpose |
|---|---|---|---|---|
| `debug` | Local/staging | Developer diagnostics | Debug key | Daily development |
| `staging` | Staging | Privacy-safe diagnostics | Internal key | QA and device acceptance |
| `release` | Production | Minimal telemetry | Protected release key | Pilot/production |

CI will run formatting, lint, unit tests, static analysis, debug assembly, and
selected Compose tests. Signed release builds are produced only from protected
tags/branches after staging acceptance.

## 12. Known risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Mobile duplicates a timed-out sale | Financial duplication | Idempotency key contract, disabled repeat submit, server lookup before retry |
| Stale branch stock during sale | Oversell | Timestamped cache, refresh at checkout, preserve server soft-block rules |
| Printer unreachable after payment | Operational delay | Sale commits independently; fallback, retry, and reprint from confirmed sale |
| Mobile and web calculate differently | Reconciliation errors | Server preview/authority; shared fixtures; never fork core calculation rules |
| Credentials or PII leak through logs | Security/privacy incident | Central redaction, release logging policy, security review |
| Scope grows before cashier flow stabilizes | Delayed pilot | P0 vertical slice first; phase exit gates and explicit P1/P2 backlog |
| API lacks mobile-safe idempotency/sync semantics | Unsafe offline writes | Start online-commit; document and implement backend contract before expansion |

## 13. Immediate implementation sequence

The build begins only after this document exists. The first implementation turn
will now:

1. Create the Gradle/Android project and deterministic version catalog.
2. Add the Compose theme and reusable mobile components.
3. Implement the navigation shell and the approved local-data screens.
4. Add initial unit/UI smoke tests.
5. Build a debug APK.
6. Install it with ADB on the connected Samsung phone.
7. Launch it and capture device-level evidence of the installed package.

## 14. Decisions that still need product confirmation before release

- Final application ID and public app name
- Production/staging base URLs and mobile authentication policy
- Supported minimum Android version after device inventory review
- Exact printer/scanner models and connection modes
- Whether any checkout method must operate fully offline
- Play Store, private Play, MDM, or direct enterprise distribution
- Analytics/crash reporting provider and privacy policy
- Final brand assets, launcher icon, and receipt/store naming

These decisions do not block the foundation build or staging prototype.

## 15. Architecture references

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [Android offline-first guidance](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Jetpack Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [Navigation 3 release notes](https://developer.android.com/jetpack/androidx/releases/navigation3)
- [Android Gradle Plugin 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
