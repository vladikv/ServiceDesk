package com.servicedesk.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityUsersProperties {
    private List<Account> users = new ArrayList<>();

    public List<Account> getUsers() {
        return users;
    }

    public void setUsers(List<Account> users) {
        this.users = users;
    }

    public List<String> getAssignableUsernames() {
        return users.stream()
                .filter(account -> account.getRole() != null)
                .filter(account -> account.getRole().equalsIgnoreCase("AGENT")
                        || account.getRole().equalsIgnoreCase("ADMIN"))
                .map(Account::getUsername)
                .filter(username -> username != null && !username.isBlank())
                .toList();
    }

    public boolean isAssignable(String username) {
        return username != null && getAssignableUsernames().stream()
                .anyMatch(configuredUsername -> configuredUsername.equals(username));
    }

    public static class Account {
        private String username;
        private String password;
        private String role;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
