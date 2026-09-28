package com.servicedesk.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final DeskUserRepository users;
    private final UserManagementService userManagement;
    private final BootstrapAdminProperties properties;

    public BootstrapAdminInitializer(
            DeskUserRepository users,
            UserManagementService userManagement,
            BootstrapAdminProperties properties) {
        this.users = users;
        this.userManagement = userManagement;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        if (properties.getUsername() == null || properties.getUsername().isBlank()
                || properties.getPassword() == null || properties.getPassword().isBlank()) {
            throw new IllegalStateException(
                    "No user accounts exist. Configure BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD for first startup.");
        }
        userManagement.createBootstrapAdmin(properties.getUsername(), properties.getPassword());
    }
}
