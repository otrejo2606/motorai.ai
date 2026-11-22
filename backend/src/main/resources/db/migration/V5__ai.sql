-- V5: module ai (design D3 and D4). Every model call is recorded; output_json never holds a clear identity number.

CREATE SCHEMA IF NOT EXISTS ai;

CREATE TABLE ai.ai_inference (
    id                uuid          NOT NULL,
    use_case          varchar(40)   NOT NULL,
    status            varchar(20)   NOT NULL,
    provider          varchar(60)   NOT NULL,
    model             varchar(120)  NOT NULL,
    prompt_version    varchar(40)   NOT NULL,
    subject_ref       uuid,
    input_refs        text,
    output_json       text,
    confidence        numeric(5, 4),
    explanation       varchar(2000),
    error_code        varchar(60),
    latency_ms        bigint,
    input_tokens      integer,
    output_tokens     integer,
    cost_usd          numeric(12, 6),
    reviewer          varchar(255),
    human_decision    varchar(60),
    human_decision_at timestamptz,
    correlation_id    uuid,
    created_at        timestamptz   NOT NULL,
    created_by        varchar(255)  NOT NULL,
    updated_at        timestamptz   NOT NULL,
    updated_by        varchar(255)  NOT NULL,
    CONSTRAINT ai_inference_pkey PRIMARY KEY (id)
);

CREATE INDEX ix_ai_inference_use_case ON ai.ai_inference (use_case, created_at);
CREATE INDEX ix_ai_inference_subject ON ai.ai_inference (subject_ref);
