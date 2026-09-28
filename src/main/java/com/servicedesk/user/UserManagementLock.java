package com.servicedesk.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_management_lock")
public class UserManagementLock {
    @Id
    @Column(name = "lock_id")
    private short lockId;

    protected UserManagementLock() {
    }
}
