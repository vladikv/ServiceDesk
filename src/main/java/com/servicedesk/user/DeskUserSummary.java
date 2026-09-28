package com.servicedesk.user;

import java.time.Instant;

public record DeskUserSummary(String username, DeskUserRole role, boolean enabled, Instant createdAt, String createdBy) {
    public static DeskUserSummary from(DeskUser user) {
        return new DeskUserSummary(user.getUsername(), user.getRole(), user.isEnabled(), user.getCreatedAt(), user.getCreatedBy());
    }
}
