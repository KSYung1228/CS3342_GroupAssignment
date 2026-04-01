package com.shoprentals.v2.model;

public class Accounting extends User {
    public Accounting(String userId, String username, String password) {
        super(userId, username, password);
    }

    public boolean confirmPaymentTransfer(Payment payment) {
        return payment.verifyPayment();
    }

    public void alterRentByStoreStatus(LeaseContract contract, Shop shop) {
        if (shop.getStatus() == ShopStatus.UNDER_REPAIR) {
            contract.setBaseRent(contract.getBaseRent() * 0.8);
        } else if (shop.getStatus() == ShopStatus.CLOSED) {
            contract.setBaseRent(contract.getBaseRent() * 0.9);
        }
    }

    public void inspectStoreStatus(Shop shop) {
        System.out.println("Accounting inspected: " + shop);
    }
}
