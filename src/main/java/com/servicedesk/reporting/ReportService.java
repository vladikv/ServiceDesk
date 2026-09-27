package com.servicedesk.reporting;

import com.servicedesk.request.RequestStatus;
import com.servicedesk.request.ServiceRequestRepository;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final ServiceRequestRepository requests;

    public ReportService(ServiceRequestRepository requests) {
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public RequestReport summary(Authentication actor) {
        boolean authorized = actor != null && actor.isAuthenticated()
                && actor.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals("ROLE_AGENT")
                                || authority.getAuthority().equals("ROLE_ADMIN"));
        if (!authorized) {
            throw new AccessDeniedException("Only agents and admins may view reports.");
        }
        var all = requests.findAll();
        Map<RequestStatus, Long> byStatus = Arrays.stream(RequestStatus.values())
                .collect(Collectors.toMap(status -> status,
                        status -> all.stream().filter(request -> request.getStatus() == status).count()));
        long slaBreaches = all.stream().filter(request -> request.getSlaBreachedAt() != null).count();
        return new RequestReport(all.size(), byStatus, slaBreaches);
    }
}
