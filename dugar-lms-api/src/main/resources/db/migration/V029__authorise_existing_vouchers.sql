UPDATE voucher_headers
SET
    status = 'AUTHORISED',
    submitted_by = COALESCE(submitted_by, created_by, updated_by),
    submitted_at = COALESCE(submitted_at, created_at, updated_at, CURRENT_TIMESTAMP),
    authorised_by = COALESCE(authorised_by, submitted_by, created_by, updated_by),
    authorised_at = COALESCE(authorised_at, submitted_at, created_at, updated_at, CURRENT_TIMESTAMP),
    updated_by = COALESCE(updated_by, authorised_by, submitted_by, created_by),
    updated_at = CURRENT_TIMESTAMP
WHERE status IN ('SUBMITTED', 'REOPENED', 'REJECTED')
  AND authorised_at IS NULL;
