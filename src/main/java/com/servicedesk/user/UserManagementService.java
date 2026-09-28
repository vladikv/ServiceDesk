package com.servicedesk.user;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9._-]{3,120}");
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final DeskUserRepository users;
    private final UserManagementLockRepository locks;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Autowired
    public UserManagementService(
            DeskUserRepository users,
            UserManagementLockRepository locks,
            PasswordEncoder passwordEncoder) {
        this(users, locks, passwordEncoder, Clock.systemUTC());
    }

    UserManagementService(
            DeskUserRepository users,
            UserManagementLockRepository locks,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.users = users;
        this.locks = locks;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DeskUserSummary> list(Authentication actor) {
        requireAdmin(actor);
        return users.findAllByOrderByUsernameAsc().stream().map(DeskUserSummary::from).toList();
    }

    @Transactional
    public DeskUserSummary create(Authentication actor, String username, String password, DeskUserRole role) {
        String creator = requireAdmin(actor);
        return DeskUserSummary.from(create(username, password, role, creator));
    }

    @Transactional
    public DeskUserSummary setEnabled(Authentication actor, String username, boolean enabled) {
        requireAdmin(actor);
        String normalized = normalizeUsername(username);
        locks.findByIdForUpdate((short) 1)
                .orElseThrow(() -> new IllegalStateException("User-management lock is missing."));
        DeskUser user = findUser(normalized);
        if (user.isEnabled() == enabled) {
            return DeskUserSummary.from(user);
        }
        if (!enabled && user.isEnabled() && user.getRole() == DeskUserRole.ADMIN
                && users.countByRoleAndEnabledTrue(DeskUserRole.ADMIN) <= 1) {
            throw new IllegalStateException("The last active administrator cannot be disabled.");
        }
        user.setEnabled(enabled);
        return DeskUserSummary.from(users.save(user));
    }

    @Transactional
    public DeskUserSummary resetPassword(Authentication actor, String username, String newPassword) {
        requireAdmin(actor);
        validatePassword(newPassword);
        DeskUser user = findUser(normalizeUsername(username));
        user.resetPassword(passwordEncoder.encode(newPassword));
        return DeskUserSummary.from(users.save(user));
    }

    @Transactional(readOnly = true)
    public boolean isAssignableAgent(String username) {
        if (username == null) {
            return false;
        }
        return users.findByUsernameAndEnabledTrue(username)
                .map(user -> user.getRole() == DeskUserRole.AGENT || user.getRole() == DeskUserRole.ADMIN)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<String> assignableUsernames() {
        return users.findAllByEnabledTrueAndRoleInOrderByUsernameAsc(List.of(DeskUserRole.AGENT, DeskUserRole.ADMIN))
                .stream().map(DeskUser::getUsername).toList();
    }

    @Transactional
    DeskUser createBootstrapAdmin(String username, String password) {
        String normalized = normalizeUsername(username);
        locks.findByIdForUpdate((short) 1)
                .orElseThrow(() -> new IllegalStateException("User-management lock is missing."));
        if (users.count() > 0) {
            DeskUser existingBootstrap = users.findById(normalized).orElse(null);
            if (existingBootstrap != null && existingBootstrap.getRole() == DeskUserRole.ADMIN
                    && existingBootstrap.isEnabled()) {
                return existingBootstrap;
            }
            throw new IllegalStateException("Bootstrap administrator can only be created before other accounts exist.");
        }
        return create(normalized, password, DeskUserRole.ADMIN, "bootstrap");
    }

    private DeskUser create(String username, String password, DeskUserRole role, String createdBy) {
        String normalized = normalizeUsername(username);
        validatePassword(password);
        if (role == null) {
            throw new IllegalArgumentException("A role is required.");
        }
        if (users.existsById(normalized)) {
            throw new IllegalArgumentException("That username is already in use.");
        }
        DeskUser user = new DeskUser(
                normalized, passwordEncoder.encode(password), role, true, clock.instant(), createdBy);
        return users.save(user);
    }

    private DeskUser findUser(String username) {
        return users.findById(username)
                .orElseThrow(() -> new IllegalArgumentException("User " + username + " was not found."));
    }

    private static String normalizeUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("Username is required.");
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Username must be 3-120 characters using letters, numbers, dot, dash, or underscore.");
        }
        return normalized;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Password must be at least 12 characters and no more than 72 UTF-8 bytes.");
        }
    }

    private static String requireAdmin(Authentication actor) {
        if (actor == null || !actor.isAuthenticated() || actor.getName() == null
                || actor.getAuthorities().stream().noneMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))) {
            throw new AccessDeniedException("Only administrators may manage user accounts.");
        }
        return actor.getName();
    }
}
