# Production readiness

Last reviewed: 21 July 2026

## Current disposition

The Android client is ready for staging and a controlled store pilot. It is not
yet ready for a production-signed release because the production environment,
release identity, supported printer hardware, and operational monitoring
choices have not been supplied.

The release build fails closed. `verifyReleaseConfiguration` rejects a missing
or non-HTTPS API URL, placeholder hosts, absent signing properties, and a
missing keystore before a release APK can be assembled.

## Engineering evidence

| Area | Implemented evidence |
|---|---|
| Core workflows | Authentication, branch/register context, POS, checkout, inventory, activity, customers, vouchers, cash, reconciliation, expenses, approvals, documents, catalog, administration, and reports |
| Financial safety | Per-attempt idempotency key, duplicate-submit guard, durable uncertain-result journal, and explicit recovery before retry |
| Credential protection | Session token encrypted with Android Keystore AES-GCM; application backup disabled |
| Network security | Cleartext disabled for staging/release; debug cleartext limited to localhost and the Android emulator host alias |
| Release optimization | Minified and resource-shrunk staging build, R8 rules, baseline profile, and startup profile packaged into the APK |
| Data durability | Room schema version 2 and an instrumented version 1 to 2 migration test that preserves a held cart and creates the checkout-attempt journal |
| Accessibility | Compose semantics, touch-target and contrast linting, and a catalog test at 200% font scale |
| Automation | JVM tests, Android lint, debug/staging assembly, and API 35 connected tests in GitHub Actions |
| Business documents | Native vector A4 PDF rendering, multi-page output, void watermark, multipart email with To/CC/cover note, and uncertain-result safeguards |

## Verification commands

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleStaging
.\gradlew.bat connectedDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The current local suite contains 47 JVM tests and 4 connected-device tests.
The debug APK has been installed and exercised on the connected Samsung
SM-S926U running Android 14. The minified staging APK is approximately 2.7 MB
and contains compiled baseline and startup profiles.

## Inputs that block a production release

1. Approved staging and production HTTPS API base URLs.
2. Final application ID, public application name, launcher art, and store name.
3. Protected release keystore, aliases/password delivery method, ownership, and
   key-recovery procedure.
4. Distribution choice: public Play, private Play, MDM, or direct enterprise.
5. Crash/ANR and privacy-safe telemetry provider, consent policy, and retention.
6. Exact receipt/label printer models and connection modes for hardware tests.
7. Backend confirmation that `POST /api/sales` atomically enforces the
   `X-Idempotency-Key` contract and supports lookup after an ambiguous timeout.
8. Product approval for destructive mobile administration of users, roles,
   branches, taxes, and printer endpoints. These remain intentionally
   read-only on mobile.

## Release configuration

Supply release values outside source control, for example in the CI secret
store or an untracked Gradle properties file:

```properties
INVENTORY_POS_API_URL=https://pos.example.com/api/
INVENTORY_POS_RELEASE_STORE_FILE=C:/secure/inventory-pos-release.jks
INVENTORY_POS_RELEASE_STORE_PASSWORD=...
INVENTORY_POS_RELEASE_KEY_ALIAS=...
INVENTORY_POS_RELEASE_KEY_PASSWORD=...
```

Then run:

```powershell
.\gradlew.bat verifyReleaseConfiguration
.\gradlew.bat testDebugUnitTest lintDebug connectedDebugAndroidTest
.\gradlew.bat assembleRelease
```

Never commit the keystore or credentials. Record the signed APK/AAB checksum,
version code, Git commit, signer certificate fingerprint, and API environment
in the release record.

## Dependency policy

The project intentionally remains on the Android API 36 / Android Gradle
Plugin 8.13 compatibility line. AndroidX Core 1.19 and Lifecycle 2.11 require
API 37-era tooling, while AGP 9 and Gradle 9 require a coordinated build
migration. Kotlin 2.4.10 and kotlinx-serialization 1.11.0 were independently
verified across the full CI matrix. Dependabot excludes the incompatible
tooling lines so security and compatible updates remain independently
reviewable instead of arriving in one unverifiable bundle.

## Production exit criteria

- Every blocking input above has an owner and an approved value.
- A release-signed build passes the complete verification suite.
- The pilot playbook passes on representative cashier and manager devices.
- Printer, scanner, receipt, label, process-death, upgrade, and rollback paths
  are tested against production-representative infrastructure.
- No unresolved critical or high-severity defect remains.
- Operations signs off reconciliation totals and the recovery procedure.

See [PILOT_PLAYBOOK.md](PILOT_PLAYBOOK.md) for the acceptance and rollback run.
