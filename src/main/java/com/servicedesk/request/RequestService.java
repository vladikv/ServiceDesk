package com.servicedesk.request;

import com.servicedesk.config.SecurityUsersProperties;
import com.servicedesk.config.SlaProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestService {
    private static final Map<RequestStatus, List<RequestStatus>> TRANSITIONS = transitions();
    private static final List<RequestStatus> ACTIVE_STATUSES =
            List.of(RequestStatus.NEW, RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS, RequestStatus.WAITING_FOR_REQUESTER);

    private final ServiceRequestRepository requests;
    private final RequestCommentRepository comments;
    private final RequestAuditRepository audit;
    private final SecurityUsersProperties securityUsers;
    private final SlaProperties slaProperties;
    private final Clock clock;

    @Autowired
    public RequestService(
            ServiceRequestRepository requests,
            RequestCommentRepository comments,
            RequestAuditRepository audit,
            SecurityUsersProperties securityUsers,
            SlaProperties slaProperties) {
        this(requests, comments, audit, securityUsers, slaProperties, Clock.systemUTC());
    }

    RequestService(
            ServiceRequestRepository requests,
            RequestCommentRepository comments,
            RequestAuditRepository audit,
            SecurityUsersProperties securityUsers,
            SlaProperties slaProperties,
            Clock clock) {
        this.requests = requests;
        this.comments = comments;
        this.audit = audit;
        this.securityUsers = securityUsers;
        this.slaProperties = slaProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ServiceRequest> search(
            Authentication actor,
            RequestStatus status,
            String searchText,
            boolean slaAttentionOnly) {
        String username = requireUsername(actor);
        if (slaAttentionOnly && !hasRole(actor, "AGENT", "ADMIN")) {
            throw new AccessDeniedException("Only agents and admins may filter by SLA attention.");
        }
        String requester = hasRole(actor, "AGENT", "ADMIN") ? null : username;
        String query = searchText == null || searchText.isBlank() ? "" : searchText.trim();
        return requests.search(status, requester, query, slaAttentionOnly);
    }

    @Transactional(readOnly = true)
    public ServiceRequest get(Authentication actor, Long id) {
        return accessibleRequest(actor, id);
    }

    @Transactional
    public ServiceRequest create(
            Authentication actor,
            String subject,
            String description,
            RequestPriority priority) {
        String username = requireRole(actor, "REQUESTER", "AGENT", "ADMIN");
        String cleanSubject = required(subject, "Subject");
        String cleanDescription = required(description, "Description");
        if (cleanSubject.length() > 160) {
            throw new IllegalArgumentException("Subject must be 160 characters or fewer.");
        }
        Instant now = clock.instant();
        ServiceRequest request = requests.save(new ServiceRequest(
                cleanSubject,
                cleanDescription,
                username,
                priority == null ? RequestPriority.NORMAL : priority,
                now,
                now.plus(slaProperties.getTargetDuration())));
        addAudit(request, username, "CREATED", request.getSubject(), now);
        return request;
    }

    @Transactional
    public ServiceRequest assign(Authentication actor, Long id, String agentUsername) {
        String username = requireRole(actor, "AGENT", "ADMIN");
        String assignee = required(agentUsername, "Assignee");
        if (assignee.length() > 120) {
            throw new IllegalArgumentException("Assignee name must be 120 characters or fewer.");
        }
        if (!securityUsers.isAssignable(assignee)) {
            throw new IllegalArgumentException("Assignee must be a configured agent or admin.");
        }
        ServiceRequest request = findRequest(id);
        if (request.getStatus() == RequestStatus.CLOSED) {
            throw new IllegalStateException("Closed requests cannot be assigned.");
        }
        String previous = request.getAssignedAgentUsername();
        request.assign(assignee, clock.instant());
        if (request.getStatus() == RequestStatus.NEW) {
            request.changeStatus(RequestStatus.ASSIGNED, clock.instant());
        }
        ServiceRequest saved = requests.save(request);
        addAudit(saved, username, "ASSIGNED", previous == null
                ? "Assigned to " + assignee
                : "Reassigned from " + previous + " to " + assignee, clock.instant());
        return saved;
    }

    @Transactional
    public ServiceRequest transition(Authentication actor, Long id, RequestStatus nextStatus) {
        String username = requireRole(actor, "AGENT", "ADMIN");
        if (nextStatus == null) {
            throw new IllegalArgumentException("A target status is required.");
        }
        ServiceRequest request = findRequest(id);
        List<RequestStatus> allowed = TRANSITIONS.getOrDefault(request.getStatus(), List.of());
        if (!allowed.contains(nextStatus)) {
            throw new IllegalStateException("Cannot change status from " + request.getStatus() + " to " + nextStatus + ".");
        }
        RequestStatus previous = request.getStatus();
        Instant now = clock.instant();
        request.changeStatus(nextStatus, now);
        ServiceRequest saved = requests.save(request);
        addAudit(saved, username, "STATUS_CHANGED", previous + " -> " + nextStatus, now);
        return saved;
    }

    @Transactional
    public RequestComment addComment(Authentication actor, Long id, String body) {
        String username = requireUsername(actor);
        ServiceRequest request = accessibleRequest(actor, id);
        String cleanBody = required(body, "Comment");
        if (cleanBody.length() > 5000) {
            throw new IllegalArgumentException("Comment must be 5000 characters or fewer.");
        }
        Instant now = clock.instant();
        RequestComment comment = comments.save(new RequestComment(id, username, cleanBody, now));
        addAudit(request, username, "COMMENT_ADDED", "Comment added", now);
        return comment;
    }

    @Transactional(readOnly = true)
    public List<RequestComment> comments(Authentication actor, Long id) {
        accessibleRequest(actor, id);
        return comments.findByRequestIdOrderByCreatedAtAsc(id);
    }

    @Transactional(readOnly = true)
    public List<RequestAuditEntry> history(Authentication actor, Long id) {
        accessibleRequest(actor, id);
        return audit.findByRequestIdOrderByOccurredAtAsc(id);
    }

    @Transactional
    public int markSlaBreaches() {
        Instant now = clock.instant();
        List<ServiceRequest> overdue = requests.findByStatusInAndSlaBreachedAtIsNull(ACTIVE_STATUSES).stream()
                .filter(request -> request.isSlaOverdueAt(now))
                .toList();
        for (ServiceRequest request : overdue) {
            request.markSlaBreached(now);
            ServiceRequest saved = requests.save(request);
            addAudit(saved, "system", "SLA_BREACHED", "Request flagged for agent attention.", now);
        }
        return overdue.size();
    }

    public Duration slaTargetDuration() {
        return slaProperties.getTargetDuration();
    }

    public List<RequestStatus> allowedTransitions(ServiceRequest request) {
        return TRANSITIONS.getOrDefault(request.getStatus(), List.of());
    }

    private ServiceRequest accessibleRequest(Authentication actor, Long id) {
        String username = requireUsername(actor);
        ServiceRequest request = findRequest(id);
        if (!hasRole(actor, "AGENT", "ADMIN") && !request.getRequesterUsername().equals(username)) {
            throw new AccessDeniedException("You may only access your own service requests.");
        }
        return request;
    }

    private ServiceRequest findRequest(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Request id is required.");
        }
        return requests.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Service request " + id + " was not found."));
    }

    private void addAudit(ServiceRequest request, String actor, String action, String details, Instant now) {
        audit.save(new RequestAuditEntry(request.getId(), actor, action, details, now));
    }

    private static String requireUsername(Authentication actor) {
        if (actor == null || !actor.isAuthenticated() || actor.getName() == null || actor.getName().isBlank()) {
            throw new AccessDeniedException("Authentication is required.");
        }
        return actor.getName();
    }

    private static String requireRole(Authentication actor, String... roles) {
        String username = requireUsername(actor);
        if (!hasRole(actor, roles)) {
            throw new AccessDeniedException("You are not authorized to perform this action.");
        }
        return username;
    }

    private static boolean hasRole(Authentication actor, String... roles) {
        if (actor == null || actor.getAuthorities() == null) {
            return false;
        }
        return actor.getAuthorities().stream()
                .anyMatch(authority -> List.of(roles).stream()
                        .anyMatch(role -> authority.getAuthority().equals("ROLE_" + role)));
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private static Map<RequestStatus, List<RequestStatus>> transitions() {
        Map<RequestStatus, List<RequestStatus>> transitions = new EnumMap<>(RequestStatus.class);
        transitions.put(RequestStatus.NEW, List.of(
                RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS, RequestStatus.WAITING_FOR_REQUESTER, RequestStatus.RESOLVED));
        transitions.put(RequestStatus.ASSIGNED, List.of(
                RequestStatus.IN_PROGRESS, RequestStatus.WAITING_FOR_REQUESTER, RequestStatus.RESOLVED));
        transitions.put(RequestStatus.IN_PROGRESS, List.of(
                RequestStatus.WAITING_FOR_REQUESTER, RequestStatus.RESOLVED));
        transitions.put(RequestStatus.WAITING_FOR_REQUESTER, List.of(
                RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS, RequestStatus.RESOLVED));
        transitions.put(RequestStatus.RESOLVED, List.of(RequestStatus.CLOSED, RequestStatus.IN_PROGRESS));
        transitions.put(RequestStatus.CLOSED, List.of());
        return Map.copyOf(transitions);
    }
}
