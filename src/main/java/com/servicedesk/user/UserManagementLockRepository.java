package com.servicedesk.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserManagementLockRepository extends JpaRepository<UserManagementLock, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lock from UserManagementLock lock where lock.lockId = :id")
    Optional<UserManagementLock> findByIdForUpdate(@Param("id") short id);
}
