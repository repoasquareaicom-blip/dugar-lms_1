# Stage 2: Generate Repayment Schedule PDF

## Behavior and API

`GET /api/contracts/{contractId}/repayment-schedule/pdf`

The existing Spring Security filter chain requires authentication. The service independently rejects unauthenticated/anonymous callers and checks the existing `ContractAccessRepository` with `ReportAccessScope.from(authentication)` before reading the contract header or schedule. Missing and inaccessible IDs both return 404. Unsupported schedules return 422 JSON; unavailable SQL/PDF generation returns 503 JSON. Successful responses use `application/pdf`, `Content-Disposition: inline` with a sanitised filename, `Cache-Control: no-store`, and `X-Content-Type-Options: nosniff`.

Within a read-only, repeatable-read transaction, the service resolves the contract number from the selected contract ID and calls `public.fn_contract_repayment_schedule(CAST(? AS VARCHAR))` exactly once. The header joins the existing borrower party master. Repayment slabs supply the header's EMI structure summary. No contract status, principal, stored IRR, repayment structure, or SQL routine is updated.

The renderer displays every returned financial value without amortisation or reconciliation. The only arithmetic is summing the returned EMI, interest, and principal columns for the totals row. The closing column in that row displays the final SQL closing balance, not a sum or forced zero. Monetary formatting uses BigDecimal and refuses to silently round additional precision. Dates use dd-MMM-yyyy; IRR uses its stored decimal scale, including 24.9970%.

PDF generation is entirely in memory. The application writes no PDF to database, permanent storage, or a temporary directory. Test-only preview files are written under target.

The Active Contracts context menu retains View and Edit and adds Generate Repayment Schedule. It opens an empty tab synchronously, fetches a blob through the existing authenticated Axios client, and navigates the tab to an object URL. Tokens are never placed in URLs. The viewer supports normal browser printing/downloading. Pop-up blocking and API errors are shown on the grid. Failed requests close the pending tab; object URLs remain available until the viewer closes.

## Dependencies, logo, and company identity

- Added Maven dependency `org.apache.pdfbox:pdfbox:3.0.8` (Apache License 2.0). The existing PDF converter depends on LibreOffice and disk files, so it was not reused for this in-memory endpoint. PDFBox has no runtime executable requirement for generation.
- The available logo was actually at `src/main/resources/report/images/dugar-logo.png` (singular). It was copied unchanged to the requested `src/main/resources/reports/images/dugar-logo.png`, preserving the original asset. Loading uses Spring ClassPathResource.getInputStream(), which works inside a packaged JAR.
- Bundled NotoSans-Regular.ttf and its SIL Open Font License under reports/fonts. Both are loaded from the classpath, so rupee rendering does not depend on Ubuntu system fonts. The test samples cover English borrower names and INR glyphs; other scripts are not covered by these fixtures.
- No existing company-name configuration/master source was found. The default legal name is taken from the existing StockStatementExcel report: DUGAR FINANCE & INVESTMENTS LIMITED. It can be overridden with the Spring property `app.company-name`; no configuration file was modified.

## Files created or modified

Backend, relative to dugar-lms-api:

| File | Change |
|---|---|
| pom.xml | Add PDFBox dependency only |
| src/main/java/dugar_lms_api/modules/reports/repaymentschedule/RepaymentScheduleData.java | Header, slab and SQL-row records |
| src/main/java/dugar_lms_api/modules/reports/repaymentschedule/RepaymentScheduleRepository.java | Read-only header/slab/function queries |
| src/main/java/dugar_lms_api/modules/reports/repaymentschedule/RepaymentScheduleService.java | Authentication, existing contract access, read-only transaction, error mapping |
| src/main/java/dugar_lms_api/modules/reports/repaymentschedule/RepaymentSchedulePdf.java | In-memory landscape renderer, repeated table headers, totals and page numbers |
| src/main/java/dugar_lms_api/modules/reports/repaymentschedule/RepaymentScheduleController.java | Inline PDF endpoint and JSON errors |
| src/main/resources/reports/images/dugar-logo.png | Copy existing logo to requested classpath path |
| src/main/resources/reports/fonts/NotoSans-Regular.ttf | Embedded font |
| src/main/resources/reports/fonts/OFL.txt | Font licence |
| src/test/java/dugar_lms_api/modules/reports/repaymentschedule/RepaymentScheduleTest.java | 11 unit/API/PDF tests |
| src/test/java/dugar_lms_api/modules/reports/repaymentschedule/RepaymentSchedulePostgresTest.java | SQL-to-PDF integration and previews |
| src/test/python/run_repayment_pdf_integration.py | Starts/stops disposable PostgreSQL fixture and invokes integration test |
| docs/repayment-schedule-stage2.md | This report |

Frontend, relative to dugar-lms-frontend:

| File | Change |
|---|---|
| src/services/repaymentScheduleService.js | Authenticated blob API and new-tab lifecycle |
| src/pages/Credit/transaction/contract/ContractGrid.jsx | New Active Contracts action and error message |
| scripts/testRepaymentSchedule.mjs | Five frontend workflow tests |
| scripts/repaymentScheduleBrowserSmoke.mjs | Local browser smoke fixture using real API client/helper and synthetic PDF |

Other pre-existing workspace edits were preserved. No SQL migration, Demand List routine, IRR routine, production configuration, or other application flow was changed.

## Verification results

- Backend compile and `mvn -q -DskipTests package`: PASS.
- Selected unit/regression run: 76 tests, zero errors/failures. Includes 11 new PDF/API tests and 65 existing Demand List, ageing, stock statement, and contract-list tests.
- Separate SQL-to-PDF integration: one test, PASS, using a fresh localhost-only PostgreSQL 18.3 database. It loads the unchanged V081 function through the existing Stage 1 fixture. Synthetic test values use contract_id 6535 and contract_number 17270; the borrower name/contract date are explicitly test data. It validates the actual repository, actual contract access query, service, and renderer. An unassigned user is denied access; before/after contract and repayment snapshots match.
- 17270 SQL results: 24 rows; total EMI 384264.00; interest 84264.14; principal 299999.86; final closing +0.14; IRR 24.9970%. PDF text assertions match the Indian-formatted values.
- Logo image and embedded font presence, landscape orientation, repeated table headers, page numbering, and literal preservation of deliberately inconsistent SQL-row values: PASS.
- Rendered 17270 and multipage last-page PNGs were visually inspected. Logo and rupee glyphs render correctly; table and totals are readable without clipping. The 24-row schedule fits one A4 landscape page.
- Packaged JAR contains the new endpoint classes, reports/images/dugar-logo.png, reports/fonts/NotoSans-Regular.ttf, font licence, and pdfbox-3.0.8.jar.
- New frontend workflow tests: 5/5 PASS. They verify authenticated-client usage, synchronous tab opening, blob navigation/cleanup, blocked pop-ups, JSON blob errors and session errors.
- Frontend `npm run build`: PASS. Existing warnings concern bundle size and stale Browserslist data.
- Broader document frontend suite: 21/22 PASS. The untouched `template preview uses original-template endpoint without starting generation` test fails with `Cannot read properties of undefined (reading 'props')`. Its harness compiles GenerateWordDocument and ReminderLetterGrid, not the modified ContractGrid. This unrelated failure was not changed as part of Stage 2.
- No connected browser was available to the UI automation tool. Live new-tab/browser-viewer interaction is not independently verified; use the manual steps below. The browser lifecycle is covered by automated mocks, and the generated PDF itself was rendered/inspected.
- V079/V080 checksums match the earlier recorded values. V081 was not modified; its SHA256 is `507f447932382da40f31646dc99aea10a3ce47c6d5d4552d0c5123d775964814`.

No production deployment or production data access was performed. Live database contract 17270 was not queried in these tests.

## Local testing

From dugar-lms-api:

```text
mvn -q -Dtest=RepaymentScheduleTest test
python src/test/python/run_repayment_pdf_integration.py
mvn -q -DskipTests package
```

The Python runner needs psycopg and PostgreSQL initdb/pg_ctl on PATH. It never reads application credentials, creates only a disposable localhost cluster, runs Maven with a guarded test database URL, and stops the cluster afterward. Logs remain in the printed temporary directory. Preview PDF/PNG files are under `target/repayment-schedule-preview/`.

From dugar-lms-frontend:

```text
node --test scripts/testRepaymentSchedule.mjs
npm run build
node scripts/repaymentScheduleBrowserSmoke.mjs
```

The last command prints an ephemeral localhost URL. Open it and click Generate Repayment Schedule. It serves the real API client/helper, verifies a synthetic bearer token locally, and returns the integration-test PDF. Verify that a second tab shows the PDF with Print/Download controls. Stop the fixture with Ctrl+C. It does not connect to any application database.

For an integrated development/staging test, use an authorised non-production database with the Stage 1 function already installed. Run the backend and frontend normally, sign in, open Credit > Active Contracts, locate 17270, right-click and choose Generate Repayment Schedule. Verify the 24 rows, stored IRR, totals and +0.14 closing balance. Repeat with an unassigned user and an unsupported contract to confirm meaningful errors. View/Edit and draft Print Contract actions should behave as before.

The API can also be tested with an existing authorised development token:

```text
curl -H "Authorization: Bearer <development-token>" -D headers.txt -o repayment.pdf http://localhost:8080/api/contracts/6535/repayment-schedule/pdf
```

## Deployment requirements (not performed)

- Existing validated Stage 1 function, current LMS schema, borrower party data, and normal contract access tables must be available. Stage 2 adds no migration.
- Build/run with the project's Java 21 baseline. Maven must resolve PDFBox and its transitive dependencies at build time. No LibreOffice or external image URL is needed for this report.
- Ship the packaged backend JAR and frontend build through the normal release process. Logo/font assets are included in the JAR and require no Ubuntu filesystem path or system font installation.
- Confirm the existing legal-name default, or set `app.company-name` through normal Spring configuration if the deployment has a configured company identity.
- Complete the authenticated development/staging browser smoke test before production deployment. This work does not deploy anything.
