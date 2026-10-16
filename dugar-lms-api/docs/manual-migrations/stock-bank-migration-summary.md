# Local Stock Bank migration completed

Target: configured local development `127.0.0.1:5434/dugar_lms`; PostgreSQL reports loopback port 5432 behind forwarding. No production or AWS actions.

## Migration counts

- Contract details before/after: **6,106 / 6,106**.
- Named assignments: **51**; all **51 migrated to Bank ID 1**.
- At the locked execution snapshot: **15 generic UNION BANK OF INDIA + 36 exact Cash Credit names**. Earlier preflight had 30/21; names changed during the intervening period. The approved generic-name alias covers the remaining 15 without altering any text.
- Existing non-null IDs before migration: **0**. The script preserves non-null IDs on reruns.
- Unmatched, ambiguous, orphan IDs and duplicate contract details: **0**.
- Original text and every unrelated contract-detail field were verified unchanged before migration commit. Bank Master, including Bank ID 10 (Term Loan), and both Demand List procedure definitions were verified unchanged.
- Foreign key `fk_contract_details_stock_bank`: **validated**.
- Backup table `stock_bank_mapping_backup`: **6,106 rows**; original bank-name column retained.
- CSV backup: `stock-bank-mappings-20261015T093326Z.csv`. SHA256 `97675d70205435c641749a88cffa96164e55653f90c458828ce26f17357b807f`. Earlier preflight backup also retained.
- Migration errors: **0**. Rerun executed in a rollback transaction and verified to change no assignments/text/other fields.

## Menu and permissions

One page, menu **273**, exists under **Credit 1 ? Transaction 7**, with URL `/credit/transaction/stock-bank-marking` and code `CREDIT_TRANSACTION_STOCK_BANK_MARKING`.

Active **CREDIT role 1** and **SUPER_ADMIN role 11** have view access to the page and its two ancestors. Existing action flags and unrelated menu/permission records were preserved. Sign out/in to refresh the cached menu tree.

## Validation and tests

`stock-bank-validation.sql` passed every check at migration commit. A separate post-commit check confirms all 51 assignments remain on ID 1, no assignments lost, no orphans/duplicates/unresolved names, complete backup coverage, and a validated FK.

**Live edits detected:** the subsequent original-text/backup comparison currently flags **7** rows whose generic names changed to Cash Credit after the migration snapshot. The migration itself preserved all original text (verified before commit); these subsequent edits retain ID 1 and remain fully reconciled. They were not reverted. IDs and before/current names are recorded in `stock-bank-migration-completed.json`. This count is a point-in-time observation while the application remains in use.

**50 backend tests passed**, including an explicit local database acceptance test. It checks active-only contract search/save, numeric bank-ID saves, transaction-to-Contract-Edit read-back, Contract-Edit-to-transaction read-back, and actual historical Stock Statement filtering by bank ID for 30 September 2026. Temporary changes to bank ID and active status were rolled back; no integration-test audit writes remain. Existing Demand List regression tests pass; procedure hashes are unchanged. Existing frontend tests (8) passed during implementation. No browser-based acceptance run or application deployment/restart was performed in this migration step.

## Artifacts

- `stock-bank-id.sql`: reviewed migration with explicit approved alias and fail-closed unmatched/ambiguous checks.
- `stock-bank-validation.sql`: reconciliation queries, including explicit alias and pre-existing-ID preservation checks.
- `stock-bank-menu.sql`: menu registration and authorized role view grants.
- `stock-bank-migration-completed.json`: complete at-commit validation outputs plus post-commit status.
- `stock-bank-before-20261015T093326Z.json`: Bank Master, menus, role permissions and procedure hash snapshot.
- `run_stock_bank_migration.py`: local-only transaction runner, backups and checks before commit.

No further database migration is pending. Refresh the login session to load the new menu; a running backend must use the updated local code for the new API handlers.
