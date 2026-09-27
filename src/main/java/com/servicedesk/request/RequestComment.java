package com.servicedesk.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "request_comment")
public class RequestComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false, length = 120)
    private String authorUsername;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    protected RequestComment() {
    }

    public RequestComment(Long requestId, String authorUsername, String body, Instant createdAt) {
        this.requestId = requestId;
        this.authorUsername = authorUsername;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
