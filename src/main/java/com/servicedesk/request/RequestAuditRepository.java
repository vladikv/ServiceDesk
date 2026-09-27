package com.servicedesk.request;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestAuditRepository extends JpaRepository<RequestAuditEntry, Long> {
    List<RequestAuditEntry> findByRequestIdOrderByOccurredAtAsc(Long requestId);
}
