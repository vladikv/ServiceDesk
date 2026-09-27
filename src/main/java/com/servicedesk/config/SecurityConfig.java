package com.servicedesk.config;

import com.servicedesk.ui.LoginView;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import com.vaadin.flow.spring.security.VaadinWebSecurity;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityUsersProperties.class, SlaProperties.class})
public class SecurityConfig extends VaadinWebSecurity {
    private static final Set<String> ALLOWED_ROLES = Set.of("REQUESTER", "AGENT", "ADMIN");

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(SecurityUsersProperties properties, PasswordEncoder encoder) {
        if (properties.getUsers() == null || properties.getUsers().isEmpty()) {
            throw new IllegalStateException("Configure at least one account under app.security.users.");
        }
        var usernames = properties.getUsers().stream()
                .map(SecurityUsersProperties.Account::getUsername)
                .filter(username -> username != null && !username.isBlank())
                .collect(Collectors.toSet());
        if (usernames.size() != properties.getUsers().size()) {
            throw new IllegalStateException("Every configured account must have a unique, non-empty username.");
        }

        UserDetails[] users = properties.getUsers().stream().map(account -> {
            String role = account.getRole() == null ? "" : account.getRole().toUpperCase(Locale.ROOT);
            if (account.getPassword() == null || account.getPassword().isBlank()) {
                throw new IllegalStateException("Set a non-empty password for each configured account.");
            }
            if (!ALLOWED_ROLES.contains(role)) {
                throw new IllegalStateException("Configured role must be REQUESTER, AGENT, or ADMIN.");
            }
            return User.withUsername(account.getUsername())
                    .password(encoder.encode(account.getPassword()))
                    .roles(role)
                    .build();
        }).toArray(UserDetails[]::new);
        return new InMemoryUserDetailsManager(users);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        setLoginView(http, LoginView.class);
    }
}
