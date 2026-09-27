CREATE TABLE service_request (
    id BIGSERIAL PRIMARY KEY,
    subject VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    requester_username VARCHAR(120) NOT NULL,
    assigned_agent_username VARCHAR(120),
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sla_due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sla_paused_at TIMESTAMP WITH TIME ZONE,
    sla_paused_seconds BIGINT NOT NULL DEFAULT 0,
    escalated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_service_request_status_updated ON service_request (status, updated_at);
CREATE INDEX idx_service_request_requester_updated ON service_request (requester_username, updated_at);
CREATE INDEX idx_service_request_sla_due ON service_request (status, escalated_at, sla_due_at);

CREATE TABLE request_comment (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES service_request (id) ON DELETE CASCADE,
    author_username VARCHAR(120) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_request_comment_request_created ON request_comment (request_id, created_at);

CREATE TABLE request_audit (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES service_request (id) ON DELETE CASCADE,
    actor_username VARCHAR(120) NOT NULL,
    action VARCHAR(40) NOT NULL,
    details VARCHAR(500),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_request_audit_request_occurred ON request_audit (request_id, occurred_at);
