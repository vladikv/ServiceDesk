package com.servicedesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.config.SlaProperties;
import com.servicedesk.user.BootstrapAdminInitializer;
import com.servicedesk.user.DatabaseUserDetailsService;
import com.servicedesk.user.DeskUserRepository;
import com.servicedesk.user.DeskUserRole;
import com.servicedesk.user.UserManagementService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:application;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "BOOTSTRAP_ADMIN_USERNAME=bootstrap-admin",
        "BOOTSTRAP_ADMIN_PASSWORD=bootstrap-admin-password",
        "SLA_TARGET_DURATION=24h"
})
class ApplicationContextTest {
    @Autowired
    private DatabaseUserDetailsService users;

    @Autowired
    private SlaProperties slaProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BootstrapAdminInitializer bootstrapAdminInitializer;

    @Autowired
    private DeskUserRepository userRepository;

    @Test
    @Transactional
    void bootstrapCreatesPersistentAdminWithEncodedPasswordAndIsIdempotent() {
        var admin = userRepository.findById("bootstrap-admin").orElseThrow();
        assertThat(users.loadUserByUsername("BOOTSTRAP-ADMIN").getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_ADMIN");
        assertThat(admin.getPasswordHash()).isNotEqualTo("bootstrap-admin-password");
        assertThat(passwordEncoder.matches("bootstrap-admin-password", admin.getPasswordHash())).isTrue();

        bootstrapAdminInitializer.run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(passwordEncoder.matches("bootstrap-admin-password",
                userRepository.findById("bootstrap-admin").orElseThrow().getPasswordHash())).isTrue();
        assertThat(slaProperties.getTargetDuration()).isEqualTo(Duration.ofHours(24));
    }

    @Autowired
    private UserManagementService userManagement;

    @Test
    @Transactional
    void adminCanCreateResetAndDisableAccountsWithoutStoringPlaintext() {
        var admin = actor("bootstrap-admin", "ADMIN");
        var agent = userManagement.create(admin, "Support.Agent", "agent-initial-password", DeskUserRole.AGENT);

        assertThat(agent.username()).isEqualTo("support.agent");
        assertThat(agent.role()).isEqualTo(DeskUserRole.AGENT);
        String storedHash = userRepository.findById(agent.username()).orElseThrow().getPasswordHash();
        assertThat(storedHash).isNotEqualTo("agent-initial-password");
        assertThat(passwordEncoder.matches("agent-initial-password", storedHash)).isTrue();
        assertThat(userManagement.assignableUsernames()).contains("support.agent");

        userManagement.resetPassword(admin, agent.username(), "agent-reset-password");
        assertThat(passwordEncoder.matches("agent-reset-password",
                userRepository.findById(agent.username()).orElseThrow().getPasswordHash())).isTrue();

        userManagement.setEnabled(admin, agent.username(), false);
        assertThat(userManagement.assignableUsernames()).doesNotContain("support.agent");
        assertThat(users.loadUserByUsername(agent.username()).isEnabled()).isFalse();
        assertThat(userManagement.list(admin)).extracting("username").contains("support.agent");
    }

    @Test
    @Transactional
    void onlyAdministratorsCanManageAccounts() {
        var requester = actor("requester", "REQUESTER");

        assertThatThrownBy(() -> userManagement.list(requester))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userManagement.create(requester, "new.user", "long-enough-password",
                DeskUserRole.AGENT)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @Transactional
    void usernamesAreUniqueWithoutCaseSensitivity() {
        var admin = actor("bootstrap-admin", "ADMIN");
        userManagement.create(admin, "new.user", "long-enough-password", DeskUserRole.REQUESTER);

        assertThatThrownBy(() -> userManagement.create(
                admin, "NEW.USER", "another-long-password", DeskUserRole.AGENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in use");
        assertThat(userRepository.findById("new.user")).isPresent();
    }

    @Test
    @Transactional
    void lastActiveAdminCannotBeDisabled() {
        var admin = actor("bootstrap-admin", "ADMIN");

        assertThatThrownBy(() -> userManagement.setEnabled(admin, "bootstrap-admin", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last active administrator");

        userManagement.create(admin, "backup-admin", "backup-admin-password", DeskUserRole.ADMIN);
        userManagement.setEnabled(admin, "bootstrap-admin", false);

        assertThat(userRepository.findById("bootstrap-admin").orElseThrow().isEnabled()).isFalse();
        assertThat(userRepository.countByRoleAndEnabledTrue(DeskUserRole.ADMIN)).isEqualTo(1);
    }

    @Test
    @Transactional
    void passwordsMustMeetBcryptSafeMinimumAndMaximum() {
        var admin = actor("bootstrap-admin", "ADMIN");

        assertThatThrownBy(() -> userManagement.create(admin, "short.user", "short", DeskUserRole.REQUESTER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userManagement.create(admin, "long.user", "é".repeat(40), DeskUserRole.REQUESTER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static UsernamePasswordAuthenticationToken actor(String username, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                username, "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
