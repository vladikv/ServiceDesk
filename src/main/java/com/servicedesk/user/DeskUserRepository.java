package com.servicedesk.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeskUserRepository extends JpaRepository<DeskUser, String> {
    List<DeskUser> findAllByOrderByUsernameAsc();

    List<DeskUser> findAllByEnabledTrueAndRoleInOrderByUsernameAsc(List<DeskUserRole> roles);

    long countByRoleAndEnabledTrue(DeskUserRole role);

    Optional<DeskUser> findByUsernameAndEnabledTrue(String username);
}
