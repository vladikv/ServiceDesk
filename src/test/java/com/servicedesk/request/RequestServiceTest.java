package com.servicedesk.request;

import com.servicedesk.config.SecurityUsersProperties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class RequestServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private ServiceRequestRepository requests;
    private RequestCommentRepository comments;
    private RequestAuditRepository audit;
    private SecurityUsersProperties securityUsers;
    private RequestService service;

    @BeforeEach
    void setUp() {
        requests = mock(ServiceRequestRepository.class);
        comments = mock(RequestCommentRepository.class);
        audit = mock(RequestAuditRepository.class);
        securityUsers = users("agent", "AGENT", "admin", "ADMIN");
        service = new RequestService(requests, comments, audit, securityUsers, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void requesterCannotReadAnotherUsersRequest() {
        ServiceRequest request = request(RequestStatus.NEW, NOW, NOW.plusSeconds(3600));
        when(requests.findById(1L)).thenReturn(Optional.of(request));

        assertThrows(AccessDeniedException.class, () -> service.get(user("bob", "REQUESTER"), 1L));
    }

    @Test
    void requesterCannotChangeRequestStatus() {
        assertThrows(AccessDeniedException.class,
                () -> service.transition(user("alice", "REQUESTER"), 1L, RequestStatus.IN_PROGRESS));
        verify(requests, never()).findById(1L);
    }

    @Test
    void waitingForRequesterPausesSlaClockUntilWorkResumes() {
        ServiceRequest request = request(RequestStatus.IN_PROGRESS, NOW, NOW.plusSeconds(7200));
        request.changeStatus(RequestStatus.WAITING_FOR_REQUESTER, NOW.plusSeconds(600));
        request.changeStatus(RequestStatus.IN_PROGRESS, NOW.plusSeconds(4200));

        assertEquals(3600, request.getSlaPausedSeconds());
        assertEquals(NOW.plusSeconds(7200 + 3600), request.getSlaDueAt().plusSeconds(request.getSlaPausedSeconds()));
        assertEquals(RequestStatus.IN_PROGRESS, request.getStatus());
    }

    @Test
    void breachedRequestEscalatesAndIsRecorded() {
        ServiceRequest request = request(RequestStatus.IN_PROGRESS, NOW.minusSeconds(49 * 3600L), NOW.minusSeconds(3600));
        when(requests.findByStatusInAndEscalatedAtIsNull(any())).thenReturn(List.of(request));
        when(requests.save(request)).thenReturn(request);

        assertEquals(1, service.escalateOverdue());
        assertEquals(RequestPriority.HIGH, request.getPriority());
        assertEquals(NOW, request.getEscalatedAt());
        verify(audit).save(any(RequestAuditEntry.class));
    }

    @Test
    void pausedRequestDoesNotEscalate() {
        ServiceRequest request = request(RequestStatus.WAITING_FOR_REQUESTER, NOW.minusSeconds(49 * 3600L), NOW.minusSeconds(3600));
        request.changeStatus(RequestStatus.WAITING_FOR_REQUESTER, NOW.minusSeconds(1800));
        when(requests.findByStatusInAndEscalatedAtIsNull(any())).thenReturn(List.of(request));

        assertEquals(0, service.escalateOverdue());
        verify(audit, never()).save(any(RequestAuditEntry.class));
    }

    @Test
    void onlyAllowedStatusTransitionsAreAccepted() {
        ServiceRequest request = request(RequestStatus.CLOSED, NOW, NOW.plusSeconds(3600));
        when(requests.findById(1L)).thenReturn(Optional.of(request));

        assertThrows(IllegalStateException.class,
                () -> service.transition(user("agent", "AGENT"), 1L, RequestStatus.IN_PROGRESS));
    }

    @Test
    void assignmentRejectsUserWithoutAgentOrAdminRole() {
        ServiceRequest request = request(RequestStatus.NEW, NOW, NOW.plusSeconds(3600));
        when(requests.findById(1L)).thenReturn(Optional.of(request));

        assertThrows(IllegalArgumentException.class,
                () -> service.assign(user("agent", "AGENT"), 1L, "requester"));
    }

    @Test
    void assignmentPromotesNewRequestToAssigned() {
        ServiceRequest request = request(RequestStatus.NEW, NOW, NOW.plusSeconds(3600));
        when(requests.findById(1L)).thenReturn(Optional.of(request));
        when(requests.save(request)).thenReturn(request);

        ServiceRequest assigned = service.assign(user("agent", "AGENT"), 1L, "admin");

        assertEquals("admin", assigned.getAssignedAgentUsername());
        assertEquals(RequestStatus.ASSIGNED, assigned.getStatus());
    }

    private static ServiceRequest request(RequestStatus status, Instant created, Instant due) {
        ServiceRequest request = new ServiceRequest("Laptop issue", "Device will not start", "alice",
                RequestPriority.NORMAL, created, due);
        if (status != RequestStatus.NEW) {
            request.changeStatus(status, created);
        }
        return request;
    }

    private static UsernamePasswordAuthenticationToken user(String name, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                name, "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static SecurityUsersProperties users(String... usernamesAndRoles) {
        SecurityUsersProperties properties = new SecurityUsersProperties();
        List<SecurityUsersProperties.Account> accounts = new java.util.ArrayList<>();
        for (int index = 0; index < usernamesAndRoles.length; index += 2) {
            SecurityUsersProperties.Account account = new SecurityUsersProperties.Account();
            account.setUsername(usernamesAndRoles[index]);
            account.setRole(usernamesAndRoles[index + 1]);
            account.setPassword("test-password");
            accounts.add(account);
        }
        properties.setUsers(accounts);
        return properties;
    }
}
