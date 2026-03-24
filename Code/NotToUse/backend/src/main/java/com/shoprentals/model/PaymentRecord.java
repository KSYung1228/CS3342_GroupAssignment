package com.shoprentals.model;

import java.time.LocalDateTime;

/**
 * Tracks a single payment transaction for a lease contract.
 */
public class PaymentRecord {
    private String id;
    private String contractId;
    private double amount;
    private LocalDateTime payTime;
    private String payMethod;  // BANK_TRANSFER, CHEQUE, ONLINE
    private String status;     // PENDING, CONFIRMED, REFUNDED
    private String note;

    public PaymentRecord() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }

    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
