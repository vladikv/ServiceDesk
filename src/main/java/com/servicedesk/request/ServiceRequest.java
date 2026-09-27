package com.servicedesk.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "service_request")
public class ServiceRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 120)
    private String requesterUsername;

    @Column(length = 120)
    private String assignedAgentUsername;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private RequestStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private RequestPriority priority;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Instant slaDueAt;

    private Instant slaPausedAt;

    @Column(nullable = false)
    private long slaPausedSeconds;

    private Instant slaBreachedAt;

    @Version
    private long version;

    protected ServiceRequest() {
    }

    public ServiceRequest(
            String subject,
            String description,
            String requesterUsername,
            RequestPriority priority,
            Instant createdAt,
            Instant slaDueAt) {
        this.subject = subject;
        this.description = description;
        this.requesterUsername = requesterUsername;
        this.priority = priority;
        this.status = RequestStatus.NEW;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.slaDueAt = slaDueAt;
    }

    public void assign(String agentUsername, Instant now) {
        this.assignedAgentUsername = agentUsername;
        this.updatedAt = now;
    }

    public void changeStatus(RequestStatus nextStatus, Instant now) {
        if (nextStatus == RequestStatus.WAITING_FOR_REQUESTER && status != RequestStatus.WAITING_FOR_REQUESTER) {
            slaPausedAt = now;
        } else if (status == RequestStatus.WAITING_FOR_REQUESTER && nextStatus != RequestStatus.WAITING_FOR_REQUESTER) {
            slaPausedSeconds += Math.max(0, now.getEpochSecond() - slaPausedAt.getEpochSecond());
            slaPausedAt = null;
        }
        status = nextStatus;
        updatedAt = now;
    }

    public void markSlaBreached(Instant now) {
        if (slaBreachedAt == null) {
            slaBreachedAt = now;
        }
        updatedAt = now;
    }

    public boolean isSlaOverdueAt(Instant now) {
        return slaPausedAt == null && slaBreachedAt == null
                && now.isAfter(slaDueAt.plusSeconds(slaPausedSeconds));
    }

    public Long getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public String getRequesterUsername() {
        return requesterUsername;
    }

    public String getAssignedAgentUsername() {
        return assignedAgentUsername;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public RequestPriority getPriority() {
        return priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSlaDueAt() {
        return slaDueAt;
    }

    public Instant getSlaPausedAt() {
        return slaPausedAt;
    }

    public long getSlaPausedSeconds() {
        return slaPausedSeconds;
    }

    public Instant getSlaBreachedAt() {
        return slaBreachedAt;
    }
}
