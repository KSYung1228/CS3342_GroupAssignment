package com.shoprentals.v1.model;

public class Payment {
    private final int paymentId;
    private final double amount;
    private PaymentStatus status;

    public Payment(int paymentId, double amount) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = PaymentStatus.UNVERIFIED;
    }

    public Payment(int paymentId, double amount, PaymentStatus status) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = status;
    }

    public boolean verifyPayment() {
        if (amount <= 0) {
            return false;
        }
        this.status = PaymentStatus.CONFIRMED;
        return true;
    }

    public int getPaymentId() {
        return paymentId;
    }

    public double getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}
