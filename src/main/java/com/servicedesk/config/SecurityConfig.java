package com.servicedesk.config;

import com.servicedesk.ui.LoginView;
import com.servicedesk.user.BootstrapAdminProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import com.vaadin.flow.spring.security.VaadinWebSecurity;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({SlaProperties.class, BootstrapAdminProperties.class})
public class SecurityConfig extends VaadinWebSecurity {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.requestMatchers(
                "/actuator/health", "/actuator/health/**").permitAll());
        super.configure(http);
        setLoginView(http, LoginView.class);
    }
}
