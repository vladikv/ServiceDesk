ALTER TABLE service_request RENAME COLUMN escalated_at TO sla_breached_at;

DROP INDEX idx_service_request_sla_due;

CREATE INDEX idx_service_request_sla_due ON service_request (status, sla_breached_at, sla_due_at);
