CREATE TABLE desk_user (
    username VARCHAR(120) PRIMARY KEY,
    password_hash VARCHAR(60) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('REQUESTER', 'AGENT', 'ADMIN')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_desk_user_enabled_role ON desk_user (enabled, role);

CREATE TABLE user_management_lock (
    lock_id SMALLINT PRIMARY KEY CHECK (lock_id = 1)
);

INSERT INTO user_management_lock (lock_id) VALUES (1);
