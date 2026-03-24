package com.shoprentals.model;

/**
 * Represents a merchant (tenant) who can apply for shop leases.
 */
public class Merchant {
    private String id;
    private String name;
    private String phone;
    private String email;
    private String businessLicense;
    private String status; // PENDING, APPROVED, REJECTED
    private String passwordHash;

    public Merchant() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getBusinessLicense() { return businessLicense; }
    public void setBusinessLicense(String businessLicense) { this.businessLicense = businessLicense; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
