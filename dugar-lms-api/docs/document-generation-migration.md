# Document Generation migration

## Inspection and compatibility

- Next available migration: **V067** (V066 is the latest existing migration). V000–V066 are unchanged.
- `menus`: bigint primary key `menu_id`, self-referencing `parent_id`, unique `menu_code`, name/path/type and active/visible flags.
- `roles`: bigint primary key `role_id`, unique `role_code` and `role_name`, active flag.
- `role_permissions`: unique `(role_id, menu_id)`, foreign keys to both tables, separate view/add/edit/delete flags and audit timestamps. The module's authorization checks `can_view`.
- The repository POM uses Spring Boot **4.1.0**, Java 21, rather than the Spring Boot 3 stated in the brief. No dependency versions were changed.
- The configured local database (`localhost:5432/dugar_lms_2.0`) rejected the configured credentials. Its current schema, Flyway history and rows could not be verified. Neither ProLMS nor CoreLMS was contacted.
- Read-only inspection of the bundled PostgreSQL archive `dugar_lms_2.0_aug2.sql` found 176 menus, 176 permission rows and the role `SUPER_ADMIN` with code `SUPER_ADMIN_1`. This is an older snapshot, not current environment data. It contains the Operations root code `OPERATIONS_53` and its `Transaction` child `TRANSACTION_55`, plus other nested menus named Operations. Root/parent scoping is therefore essential. V060 subsequently installs the document menus; V062 adds canonical SUPER_ADMIN.
- Migration supports canonical ancestors and legacy names/codes, including singular Transaction. Existing parent labels, menu codes and URL paths remain unchanged. Numeric role codes are supported when their role names identify SUPER_ADMIN or OPERATIONS. Unrecognized role aliases require explicit review; no roles or user assignments are created or changed.
- Missing document menus, missing/inactive required roles, or ambiguous fallback ancestors fail the migration transaction. This avoids a silent partial grant or a duplicate menu tree.

## Changes

- `src/main/resources/db/migration/V067__rename_document_menus_and_restore_permissions.sql`: renames both leaves, places them under the requested existing parents, ensures required ancestors are active/visible, grants SUPER_ADMIN both branches and OPERATIONS the generation branch. Updates only `can_view` on existing grants; new grants default other flags to false. No permission revocations or unrelated grants.
- React `WordTemplateMaster.jsx` and `GenerateWordDocument.jsx`: headings, field labels, dialogs, notices and errors use Document. Existing Generate & Download/upload actions and DOCX inputs remain.
- React utilities `documentMenuLabels.js`, `menuNormalizer.js`, `menuRouteMap.js`: desktop/mobile navigation receives renamed labels even from an existing login's cached tree. Both old/new names resolve to unchanged routes. There is no separate document breadcrumb component or browser-title implementation in this module.
- `wordTemplateService.js`: default download filenames use `document` and retain `.docx`.
- Backend `WordTemplateService`: generation-authorized users can read active template groups/templates; inactive groups are excluded from their template catalog. Management, original-template download and mutations still require Template Master access.
- Backend `WordDocumentGenerationController`: validation accepts either the existing master or generation menu permission. Previously OPERATIONS could open the generation page but catalog/validation requests returned 403.
- Backend message-only changes: `WordTemplateController.java`, `GenerateWordDocumentRequest.java`, `WordDocumentGenerationService.java`, and `WordDocxTemplateEngine.java`, plus the two access-control files above. Java names, API endpoints, database table names, storage configuration, DOCX MIME type and processing remain compatible.

## Local verification

The opt-in `DocumentMenuMigrationTest` requires an isolated disposable PostgreSQL database named **document_menu_test**, loopback IPv4 host, and an explicit port. It cleans that database's public schema. Never point it at an application database. The fixtures and V067 are installed through Flyway; there are no manual updates to application databases.

```powershell
$env:DOCUMENT_MENU_TEST_URL='jdbc:postgresql://127.0.0.1:55467/document_menu_test'
mvn -q '-Dtest=DocumentMenuMigrationTest,DocumentGenerationAccessTest,DocumentDocxRoundTripTest,WordDocumentGenerationServiceTest,WordTemplateStorageServiceTest' test
```

Run from `dugar-lms-api`; test user is `postgres` with an empty password in the isolated trust-authenticated test cluster. Without the environment variable, migration integration tests are skipped.

```powershell
# Run from dugar-lms-frontend
node --test scripts/testDocumentMenuLabels.mjs
npm run build
```

`document-generation-verification.sql` provides read-only schema, hierarchy, Flyway and permission queries for local inspection. A successful migration should show version 067, the two renamed leaves on their original paths, and view permissions on both SUPER_ADMIN branches and only the generation branch newly granted to OPERATIONS. Existing unrelated permissions must be compared against a pre-migration snapshot; they are deliberately not revoked.

No production deployment or database migration was performed. Compatibility is verified against fixtures representing both menu-tree styles, not live ProLMS/CoreLMS databases.

## Results (2026-10-16)

- **15 backend tests passed**, no failures/errors/skips: 5 Flyway migration scenarios, 4 catalog/validation authorization scenarios, 1 real DOCX paragraph/table merge, 2 existing generation-service tests, 3 existing storage tests.
- Flyway validated V067 and a repeat `migrate()` applied zero migrations. Missing-role and ambiguous-hierarchy tests confirmed transactional failure without partial changes. Fixtures cover legacy role aliases, different IDs and unchanged unrelated grants/CRUD flags.
- **2 frontend tests passed** for old/new/cached labels, original routes and unrelated menu preservation. `npm run build` passed; existing Browserslist age and bundle-size warnings remain.
- Read-only verification SQL executed successfully on PostgreSQL **18.3** in the isolated test cluster. The permission assertion returned zero concerns and both renamed pages retained their original routes.
- `git diff --check` passed. The full application suite and live browser workflow were not run; the configured application database credentials are invalid. All database writes during this task were Flyway migrations/test fixtures in the disposable local test database.

New test files: `DocumentMenuMigrationTest.java`, `DocumentGenerationAccessTest.java`, `DocumentDocxRoundTripTest.java`, and frontend `scripts/testDocumentMenuLabels.mjs`. Existing document tests remain unchanged.
