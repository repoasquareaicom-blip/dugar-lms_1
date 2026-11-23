CREATE TABLE document_field_rules (
    canonical_code VARCHAR(200) PRIMARY KEY,
    conversion_type VARCHAR(20) NOT NULL DEFAULT 'PRESERVE'
        CHECK (conversion_type IN ('PRESERVE','TRANSLITERATE','TRANSLATE','FORMAT')),
    context VARCHAR(150) NOT NULL DEFAULT 'general',
    format_kind VARCHAR(30) CHECK (format_kind IN ('DATE','AMOUNT_WORDS','CURRENCY')),
    updated_by VARCHAR(100), updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE document_placeholder_aliases (
    id BIGSERIAL PRIMARY KEY, alias VARCHAR(150) NOT NULL CHECK(alias ~ '^[A-Za-z][A-Za-z0-9_]*$'),
    canonical_code VARCHAR(200) NOT NULL, created_by VARCHAR(100), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_document_alias ON document_placeholder_aliases(LOWER(alias));
CREATE TABLE document_translation_cache (
    id BIGSERIAL PRIMARY KEY, target_language VARCHAR(16) NOT NULL REFERENCES document_languages(code),
    conversion_type VARCHAR(20) NOT NULL, context VARCHAR(150) NOT NULL, source_hash CHAR(64) NOT NULL,
    source_value TEXT NOT NULL, translated_value TEXT NOT NULL, model VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(50) NOT NULL, verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK(verification_status IN ('PENDING','APPROVED')),
    version INTEGER NOT NULL DEFAULT 1, verified_by VARCHAR(100), verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(target_language,conversion_type,context,source_hash,prompt_version)
);
CREATE TABLE document_translation_history (
    id BIGSERIAL PRIMARY KEY, cache_id BIGINT NOT NULL REFERENCES document_translation_cache(id),
    version INTEGER NOT NULL, translated_value TEXT NOT NULL, changed_by VARCHAR(100) NOT NULL,
    reason VARCHAR(500) NOT NULL, changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(cache_id,version)
);
CREATE TABLE ai_pricing_versions (
    id BIGSERIAL PRIMARY KEY, provider VARCHAR(30) NOT NULL DEFAULT 'OPENAI', model VARCHAR(100) NOT NULL,
    input_per_million NUMERIC(18,8) NOT NULL CHECK(input_per_million >= 0),
    cached_input_per_million NUMERIC(18,8) NOT NULL CHECK(cached_input_per_million >= 0),
    output_per_million NUMERIC(18,8) NOT NULL CHECK(output_per_million >= 0),
    tool_pricing JSONB NOT NULL DEFAULT '{}', usd_inr NUMERIC(18,8) NOT NULL CHECK(usd_inr > 0),
    multiplier NUMERIC(12,4) NOT NULL DEFAULT 3 CHECK(multiplier >= 0),
    effective_at TIMESTAMPTZ NOT NULL, is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(100), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Published token rates only. Activate a version after setting the agreed exchange rate.
INSERT INTO ai_pricing_versions(model,input_per_million,cached_input_per_million,output_per_million,usd_inr,effective_at)
VALUES ('gpt-5-mini',0.25,0.025,2.00,1,CURRENT_TIMESTAMP);
CREATE TABLE document_generation_audit (
    id UUID PRIMARY KEY, batch_id UUID, contract_id BIGINT, contract_number VARCHAR(150),
    group_name VARCHAR(150), template_id BIGINT, template_name VARCHAR(150), language_code VARCHAR(16),
    filename VARCHAR(500), output_format VARCHAR(10) NOT NULL DEFAULT 'DOCX',
    user_id BIGINT, username VARCHAR(100) NOT NULL, started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ, status VARCHAR(30) NOT NULL, failure_reason VARCHAR(500),
    cache_hits INTEGER NOT NULL DEFAULT 0, cache_misses INTEGER NOT NULL DEFAULT 0,
    preview JSONB, approved BOOLEAN NOT NULL DEFAULT FALSE, approved_by VARCHAR(100), approved_at TIMESTAMPTZ,
    template_hash CHAR(64), expires_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '30 minutes')
);
CREATE TABLE ai_api_usage (
    id BIGSERIAL PRIMARY KEY, provider_request_id VARCHAR(150), module VARCHAR(100) NOT NULL,
    feature VARCHAR(100) NOT NULL, generation_id UUID REFERENCES document_generation_audit(id),
    contract_reference VARCHAR(150), user_id BIGINT, username VARCHAR(100),
    requested_at TIMESTAMPTZ NOT NULL, responded_at TIMESTAMPTZ NOT NULL, duration_ms BIGINT NOT NULL,
    model VARCHAR(100) NOT NULL, input_tokens BIGINT, cached_input_tokens BIGINT, output_tokens BIGINT, total_tokens BIGINT,
    tool_usage JSONB NOT NULL DEFAULT '{}', status VARCHAR(30) NOT NULL, error_category VARCHAR(100), error_message VARCHAR(500),
    pricing_version BIGINT REFERENCES ai_pricing_versions(id), applied_rates JSONB NOT NULL,
    estimated_usd NUMERIC(24,12), usd_inr NUMERIC(18,8), estimated_inr NUMERIC(24,12),
    multiplier NUMERIC(12,4), billable_inr NUMERIC(24,12), reconciled_usd NUMERIC(24,12)
);
-- A request is charged once even if later associated with multiple documents.
CREATE TABLE document_generation_api_links (
    generation_id UUID NOT NULL REFERENCES document_generation_audit(id), usage_id BIGINT NOT NULL REFERENCES ai_api_usage(id),
    allocation NUMERIC(12,10) NOT NULL DEFAULT 1 CHECK(allocation > 0 AND allocation <= 1),
    PRIMARY KEY(generation_id,usage_id)
);
CREATE TABLE document_generation_translation_versions (
    generation_id UUID NOT NULL REFERENCES document_generation_audit(id), canonical_code VARCHAR(200) NOT NULL,
    cache_id BIGINT NOT NULL, cache_version INTEGER NOT NULL,
    FOREIGN KEY(cache_id,cache_version) REFERENCES document_translation_history(cache_id,version),
    PRIMARY KEY(generation_id,canonical_code)
);
CREATE INDEX idx_ai_usage_date ON ai_api_usage(requested_at DESC,id DESC);
CREATE INDEX idx_ai_usage_generation ON ai_api_usage(generation_id);
ALTER TABLE document_translation_cache ADD COLUMN origin_usage_id BIGINT REFERENCES ai_api_usage(id);
CREATE INDEX idx_document_audit_user_date ON document_generation_audit(user_id,started_at DESC);

-- Conservative defaults from the actual schema-driven canonical registry.
-- Unlisted fields (especially identifiers and numeric values) remain PRESERVE.
INSERT INTO document_field_rules(canonical_code,conversion_type,context)
SELECT '{{' || prefixes.prefix || '_' || UPPER(c.column_name) || '}}','TRANSLITERATE',
    CASE WHEN c.column_name IN ('full_name','swd_name','firm_name') THEN 'personal_name' ELSE 'address' END
FROM information_schema.columns c
CROSS JOIN (VALUES('BORROWER'),('CO_APPLICANT'),('GUARANTOR_1'),('GUARANTOR_2')) prefixes(prefix)
WHERE c.table_schema='public' AND c.table_name='party_masters'
    AND c.column_name IN ('full_name','swd_name','firm_name','address_line_1','address_line_2','city','area','state');
INSERT INTO document_field_rules(canonical_code,conversion_type,context,format_kind) VALUES
    ('{{LOAN_AMOUNT_IN_WORDS}}','FORMAT','money','AMOUNT_WORDS'),
    ('{{FORMATTED_CONTRACT_DATE}}','FORMAT','date','DATE'),
    ('{{CONTRACT_CONTRACT_DATE}}','FORMAT','date','DATE');
