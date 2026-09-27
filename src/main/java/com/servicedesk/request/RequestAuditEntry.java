package com.servicedesk.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "request_audit")
public class RequestAuditEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false, length = 120)
    private String actorUsername;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 500)
    private String details;

    @Column(nullable = false)
    private Instant occurredAt;

    protected RequestAuditEntry() {
    }

    public RequestAuditEntry(Long requestId, String actorUsername, String action, String details, Instant occurredAt) {
        this.requestId = requestId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
