package com.servicedesk.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:requests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class ServiceRequestRepositoryTest {
    @Autowired
    private ServiceRequestRepository requests;

    @Test
    void searchFiltersByRequesterStatusAndText() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        requests.saveAndFlush(new ServiceRequest("Laptop setup", "Prepare new device", "alice",
                RequestPriority.NORMAL, now, now.plusSeconds(3600)));
        requests.saveAndFlush(new ServiceRequest("VPN access", "Cannot connect remotely", "bob",
                RequestPriority.HIGH, now.plusSeconds(1), now.plusSeconds(7200)));

        assertThat(requests.search(RequestStatus.NEW, "alice", "laptop"))
                .extracting(ServiceRequest::getSubject)
                .containsExactly("Laptop setup");
        assertThat(requests.search(null, "alice", "vpn")).isEmpty();
        assertThat(requests.search(null, null, "remotely"))
                .extracting(ServiceRequest::getSubject)
                .containsExactly("VPN access");
    }
}
