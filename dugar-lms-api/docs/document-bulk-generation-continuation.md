# Document templates and bulk generation continuation

Date: 2026-10-16. This report supersedes the earlier document-auto-generation delivery notes for the current working tree.

## Inspection and completed work preserved

The interrupted tree already contained the simplified Document Template Master, language-labelled catalogue, original-template download/PDF preview endpoints, contract-grid selection, asynchronous export skeleton, Hindi field conversion/cache/auditing, permissions, and migrations V067?V073. These were extended in place. Existing routes, template IDs/files, canonical fields, legacy groups/order metadata, alias editing, billing report and the single-document API remain available. Unrelated manual-migration backups, storage contents and datasource settings were left alone.

## Current features

- Template Master edits document name, English/Hindi and optional replacement DOCX; new templates require a DOCX. Legacy group/order/logical-key metadata is resolved internally. Available Fields retains alias search/editing and English contract-value preview.
- Generation lists active templates by name and language across legacy groups. Refresh and template-change events update the catalogue. Original DOCX download and an inline PDF view use the original uploaded template without merging placeholders or calling AI.
- Word/PDF choice; one or multiple checked contract rows across pages, or every matching contract across all result pages. Changing filters clears selection. All-matching selection is snapshotted in PostgreSQL using the grid's existing filters and access predicate. More than 100 documents needs explicit UI/server confirmation. The configured maximum defaults to 100,000 contracts.
- One explicitly selected contract downloads a DOCX/PDF. Multiple contracts and all-matching exports download a ZIP. Partial exports contain successful documents plus errors.csv; paginated errors and progress counters are available on screen. Failed/expired exports cannot download partial files.
- Hindi conversion translates required field values only, preserving document text/layout. Exact source/context/conversion/prompt identities use the PostgreSQL cache, advisory locking, and version history. Identifiers and deterministic Hindi date/amount formatting are preserved. Bulk uses validated Hindi values with unchanged-value approval; existing review/correction APIs remain available. Provider calls are disabled by default and tests use fake transports.
- Generate-menu permission, active user, ownership and current contract eligibility/scope are checked. Downloads recheck access to successful contracts. Template activity, language and original-file hash are rechecked for every item. Missing authentication now gives a restricted report scope, fixing a failure exposed by the wider test suite.

## Bulk lifecycle hardening

Two workers and eight queued jobs bound concurrency. Artifacts are disk-backed, output bytes are limited during writes, and the sum of uncompressed document sizes is also capped (default 1 GiB). ZIP entry names are sanitized and unique; error CSV cells are quoted and formula prefixes escaped. Large downloads stream to a browser Save File handle when available; the fallback buffers only artifacts up to 64 MiB.

The cleanup scheduler runs every minute. Per-instance heartbeats cover queued and running jobs, including long individual translations/conversions. An expired lease is atomically marked failed with pending-item counters and unfinished audits. Workers check their lease before each item and before publishing, so an expired worker cannot make an artifact downloadable. In-progress jobs are deliberately **not replayed after a crash**: they fail within the 20-minute lease window and require a new export, avoiding duplicate AI charges. Graceful shutdown expires this instance's jobs immediately. Completed artifacts remain downloadable across restarts until their 24-hour expiry; keep the export directory persistent and shared if running multiple application instances.

Failed jobs remove partial artifacts. Expired-job deletion retries when a file is temporarily locked rather than marking it cleaned prematurely. PDF profiles/conversion files live beside the input (inside the bulk job directory), use bounded subprocess timeouts, validate the PDF signature and kill timed-out subprocesses. Temporary download streams delete their files on close. Expired/failed preview payloads are cleared; audit and translation history remain. Template replacement/deletion cleans old files after transaction commit, and new uploads are cleaned on rollback.

## Flyway migrations

Apply through normal Flyway startup, after reviewing the target history/checksums and rehearsing on a sanitized copy. No application/production database was changed here. Do not repair checksums or edit applied scripts.

| Version | Purpose |
|---|---|
| V067 | Existing document menu labels and restored permissions |
| V068 | Language catalogue, English backfill, logical document keys and active variant uniqueness |
| V069 | Field aliases/rules, PostgreSQL translation cache/history, audits and AI usage/billing |
| V070 | AI administration/report menus and grants |
| V071 | Internal default template group for simplified uploads |
| V072 | Retire AI Settings menu while retaining backend pricing/cache and usage reporting |
| V073 | Durable export jobs/items, indexes and future audit/template reference protection |
| V074 | Forward-only export status constraints and lease/expiry indexes |

V074 is new in this continuation; prior migration contents were preserved. NOT VALID constraints retain legacy rows while enforcing new writes. Isolated tests apply the document-module migration chain, validate checksums and repeat migrate with no changes; this is not proof of full production schema equivalence.

## Verification

See document-enhancement-test-results.txt for final counts and commands. Frontend behavioral tests cover language labels/refresh, Word/PDF payloads, one/multiple/all-matching selection, large confirmation, filter resets, original preview, aliases and menu routes. PostgreSQL tests cover original generation/cache/history plus export ownership, current eligibility, partial ZIP contents, duplicate IDs, template-change rejection, expiry, stale-worker fencing and completed-download restart behavior. Converter tests use a controlled fake subprocess, including timeout/cleanup and byte-limit failures.

## Deployment prerequisites and pending validation

1. Review/rehearse the actual environment's Flyway history, role/menu records and active-template name collisions; back up PostgreSQL and template storage. Release backend/frontend together and verify grants. No deployment has been performed.
2. Install LibreOffice Writer and the actual English/Hindi fonts; set DOCUMENT_PDF_EXECUTABLE to its absolute executable path (Windows example: C:/Program Files/LibreOffice/program/soffice.exe). DOCUMENT_PDF_TIMEOUT_SECONDS defaults to 120, allowed range 1?600. Run conversion under a restricted service account with suitable process/network isolation for uploaded documents.
3. Set persistent WORD_TEMPLATE_STORAGE_DIR and DOCUMENT_EXPORT_STORAGE_DIR, service-user filesystem permissions, disk quotas/monitoring and adequate database connections. Export limits are DOCUMENT_EXPORT_MAX_BYTES and DOCUMENT_EXPORT_MAX_CONTRACTS. Multiple instances need the same paths/storage. Allow temporary-directory cleanup for preview files left by a hard process/host crash; durable database audit/item metadata currently has no automatic retention deletion.
4. Keep DOCUMENT_AI_ENABLED=false until operationally approved. To enable: configure server-only OPENAI_API_KEY, DOCUMENT_AI_MODEL and backend pricing/exchange rate variables documented in document-ai-configuration.md (particularly a positive DOCUMENT_AI_USD_INR). No real OpenAI request or Hindi linguistic-quality check was performed.
5. Verify real DOCX/PDF rendering with representative production templates and installed fonts, browser selection/download workflows, and large-volume performance before rollout. LibreOffice is absent locally, so actual rendered PDF layout is unverified; integration uses fake PDF/provider boundaries.

The application context smoke test was excluded because it hard-codes the application datasource and would allow Flyway to modify it. The stock-bank application-database integration test stays opt-in and was skipped. Neither was enabled to satisfy a test count. No production database, deployment or real OpenAI call was made.

## Modified files

The complete feature source/test/config/migration manifest is document-enhancement-files.txt. Main files include WordTemplateService/Controller/Repository and metadata DTOs; DocumentExportService/Controller/Request; DocumentPdfConverter; DocumentAutomationService and translation/cache/audit services; ContractListRepository and ReportAccessScope; GenerateWordDocument, WordTemplateMaster, ContractGrid, ServerDataTable, wordTemplateService and menu utilities; application.properties, frontend package.json and the document tests. Unrelated pre-existing untracked migration backups and storage files are excluded from the manifest.
