package com.servicedesk.request;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {
    @Query("""
            select request from ServiceRequest request
            where (:status is null or request.status = :status)
              and (:requester is null or request.requesterUsername = :requester)
              and (:query is null or
                   lower(request.subject) like lower(concat('%', :query, '%')) or
                   lower(request.description) like lower(concat('%', :query, '%')) or
                   lower(coalesce(request.assignedAgentUsername, '')) like lower(concat('%', :query, '%')))
            order by request.updatedAt desc
            """)
    List<ServiceRequest> search(
            @Param("status") RequestStatus status,
            @Param("requester") String requester,
            @Param("query") String query);

    List<ServiceRequest> findByStatusInAndEscalatedAtIsNull(List<RequestStatus> statuses);
}
