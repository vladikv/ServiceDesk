package com.servicedesk;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import com.servicedesk.config.SlaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:application;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "SERVICEDESK_REQUESTER_PASSWORD=requester-test-password",
        "SERVICEDESK_AGENT_PASSWORD=agent-test-password",
        "SERVICEDESK_ADMIN_PASSWORD=admin-test-password",
        "SLA_TARGET_DURATION=24h"
})
class ApplicationContextTest {
    @Autowired
    private UserDetailsService users;

    @Autowired
    private SlaProperties slaProperties;

    @Test
    void configuredUserHasExpectedRole() {
        assertThat(users.loadUserByUsername("agent").getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_AGENT");
        assertThat(slaProperties.getTargetDuration()).isEqualTo(Duration.ofHours(24));
    }
}
