package com.shoprentals.model;

/**
 * Represents a system admin or internal management user.
 */
public class Admin {
    private String id;
    private String username;
    private String passwordHash;
    private String role; // SYSTEM_ADMIN, ACCOUNTING, CONTRACT_MANAGER, INTERNAL_MANAGEMENT
    private String status; // ACTIVE, INACTIVE

    public Admin() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
