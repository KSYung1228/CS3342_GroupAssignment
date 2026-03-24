package com.shoprentals.model;

import java.time.LocalDate;

/**
 * Represents a lease contract between a merchant and a shop.
 * Uses the State pattern – the contractState field drives allowed transitions.
 */
public class LeaseContract {
    private String id;
    private String merchantId;
    private String shopId;
    private LocalDate startTime;
    private LocalDate endTime;
    private double rentPerMonth;   // calculated by RentCalculationStrategy
    private String rentCycle;      // MONTHLY, QUARTERLY, YEARLY
    private String status;         // PENDING, ACTIVE, TERMINATED, EXPIRED
    // State pattern – stored state name for persistence
    private String contractState;  // DRAFT, PENDING_APPROVAL, ACTIVE, TERMINATED, EXPIRED
    private double deposit;
    private String adminId;        // approving admin

    public LeaseContract() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getShopId() { return shopId; }
    public void setShopId(String shopId) { this.shopId = shopId; }

    public LocalDate getStartTime() { return startTime; }
    public void setStartTime(LocalDate startTime) { this.startTime = startTime; }

    public LocalDate getEndTime() { return endTime; }
    public void setEndTime(LocalDate endTime) { this.endTime = endTime; }

    public double getRentPerMonth() { return rentPerMonth; }
    public void setRentPerMonth(double rentPerMonth) { this.rentPerMonth = rentPerMonth; }

    public String getRentCycle() { return rentCycle; }
    public void setRentCycle(String rentCycle) { this.rentCycle = rentCycle; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getContractState() { return contractState; }
    public void setContractState(String contractState) { this.contractState = contractState; }

    public double getDeposit() { return deposit; }
    public void setDeposit(double deposit) { this.deposit = deposit; }

    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
}
