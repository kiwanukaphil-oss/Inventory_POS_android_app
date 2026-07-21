# Store pilot playbook

## Pilot shape

Run the first pilot in one branch with one register, two trained cashiers, and
one manager for three trading days. Use a staging backend populated with
production-shaped products, customers, permissions, taxes, and stock, but no
real customer contact details.

## Preflight

- Record the APK/AAB checksum, version, Git commit, signer fingerprint, device
  models, Android versions, API URL, branch, register, and printer models.
- Confirm device time, network, charging, paper, cash drawer, and fallback web
  terminal access.
- Verify cashier and manager accounts have intentionally different permissions.
- Take an authoritative opening stock and drawer snapshot.
- Confirm support ownership and the stop/rollback decision maker.

## Acceptance journeys

1. Sign in, select the authorized branch/register, sign out, and restore a
   valid session after relaunch.
2. Open the drawer with no float and with a formatted multi-digit amount;
   verify comma insertion never moves the cursor or changes the entered value.
3. Find products by name, SKU, barcode, category, and stock state; select a
   variant and verify branch availability.
4. Build, edit, hold, restore, and abandon a cart; attach a customer and apply
   permitted line/order discounts with the correct approval behavior.
5. Complete cash, card, mobile-money, customer-account, and split payments;
   verify balance, change, tax, discount, and tender totals.
6. Simulate an ambiguous checkout timeout. Confirm the sale remains locked,
   can be looked up by its idempotency key, and cannot be duplicated.
7. Print, reprint, and email a confirmed receipt; confirm payment remains
   complete when printing fails.
8. Receive stock, save/restore a GRN draft, adjust stock, and execute every
   supported transfer state. Compare movements with the web system.
9. Change an authorized price, exercise approval when required, review price
   history, and print a scannable EAN-13 label.
10. Find a receipt, complete a partial return and exchange, and confirm the
    server-authoritative refund and stock movements.
11. Review customer balance, aging, statement, loyalty, and notes. Issue,
    activate, verify, redeem, cancel, and—when permitted—refund a voucher.
12. Record cash movements, complete blind close and handover, explain variance,
    and compare the Z-summary and end-of-day totals with the web system.
13. Create/edit/delete an expense under the correct permission model. Approve
    and reject representative discount, stock, cash, price, and no-sale
    requests; verify self-approval is blocked.
14. Create, edit, convert, void, render, and email a quote, invoice, and
    corporate receipt. Open the resulting multi-page PDF on another device.
15. Compare daily, seven-day, thirty-day, and month-to-date sales, returns,
    tax, discount, margin, expense, and cash-flow reports with the web system.
16. Repeat critical flows with 200% font scale, screen reader, rotation,
    background/process recreation, temporary network loss, and an upgrade from
    a schema-version-1 app database.

## Daily control

At opening, record stock/drawer baselines and confirm login, sync, scanner, and
printer health. During trade, log every unexpected message, uncertain result,
manual fallback, and mismatch with timestamp and sale/document identifier. At
close, reconcile sales, tender channels, returns, expenses, drawer cash, tax,
and stock movements against the web system before the next pilot day begins.

## Stop conditions

Stop new mobile transactions immediately for any suspected duplicate or lost
sale, incorrect financial total, authentication/authorization bypass, leaked
customer information, unrecoverable checkout state, unexplained reconciliation
variance, or database migration failure.

Printer-only failure is not a reason to reverse a confirmed payment. Use the
receipt reprint/fallback path and log the incident.

## Rollback and recovery

1. Remove the Android register from service and route new work to the approved
   web fallback.
2. Preserve the device and app data while any checkout is uncertain. Do not
   clear storage or uninstall until its idempotency key is reconciled.
3. Reconcile every pending attempt against the server and complete or void it
   using the normal business workflow.
4. Export incident evidence and server audit identifiers without copying
   tokens, passwords, or unnecessary customer data.
5. Roll back by installing the last approved, correctly signed build only when
   its database downgrade/upgrade path is documented. Otherwise retain the
   current client and disable its register access server-side.
6. Rebaseline stock and cash, document the decision, then obtain manager and
   engineering approval before resuming.

## Sign-off

| Role | Confirms | Name/date |
|---|---|---|
| Cashier representative | Touch flow, speed, readability, receipt handling | |
| Store manager | Permissions, approvals, cash and day-close reconciliation | |
| Finance/operations | Sales, tax, returns, expenses, stock and tender totals | |
| Engineering | Build identity, tests, logs, recovery, no critical defects | |
| Product owner | Pilot acceptance and production rollout decision | |
