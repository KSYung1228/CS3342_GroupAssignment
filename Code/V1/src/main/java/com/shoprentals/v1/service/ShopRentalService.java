package com.shoprentals.v1.service;

import com.shoprentals.v1.model.LeaseContract;
import com.shoprentals.v1.model.Payment;
import com.shoprentals.v1.model.Shop;
import com.shoprentals.v1.model.ShopStatus;
import com.shoprentals.v1.model.SalesRecord;
import com.shoprentals.v1.model.Tenant;
import com.shoprentals.v1.pattern.factory.LeaseContractFactory;
import com.shoprentals.v1.pattern.observer.RentalEvent;
import com.shoprentals.v1.pattern.observer.RentalEventBus;
import com.shoprentals.v1.pattern.strategy.CommissionStrategy;

import java.util.ArrayList;
import java.util.List;

public class ShopRentalService {
    private final RentalEventBus eventBus;
    private int nextContractId = 1;
    private int nextRecordId = 1;
    private int nextPaymentId = 1;

    public ShopRentalService(RentalEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public List<Shop> findOpenShops(List<Shop> shops) {
        List<Shop> result = new ArrayList<>();
        for (Shop shop : shops) {
            if (shop.getStatus() == ShopStatus.OPEN) {
                result.add(shop);
            }
        }
        return result;
    }

    public LeaseContract createLeaseRequest(Tenant tenant, Shop shop, LeaseContractFactory factory, double baseRent) {
        LeaseContract contract = factory.createContract(nextContractId++, shop, tenant, baseRent);
        contract.requestApproval();
        eventBus.publish(new RentalEvent("LEASE_REQUESTED", "Tenant " + tenant.getUsername() + " requested shop " + shop.getShopNum()));
        return contract;
    }

    public void approveContract(LeaseContract contract) {
        contract.approve();
        eventBus.publish(new RentalEvent("CONTRACT_APPROVED", "Contract " + contract.getContractId() + " approved"));
    }

    public Payment submitMonthlySales(LeaseContract contract, String month, double sales, CommissionStrategy strategy) {
        SalesRecord record = new SalesRecord(nextRecordId++, month, sales);
        record.calculateCommission(strategy, contract.getCommissionRate());
        double totalRent = contract.calculateTotalRent(record.getCommissionAmount());

        eventBus.publish(new RentalEvent("MONTHLY_RECORD_UPLOADED",
                "Contract " + contract.getContractId() + " month " + month + " commission " + record.getCommissionAmount()));

        return new Payment(nextPaymentId++, totalRent);
    }

    public int getNextContractId() {
        return nextContractId;
    }

    public void setNextContractId(int nextContractId) {
        this.nextContractId = Math.max(1, nextContractId);
    }

    public int getNextRecordId() {
        return nextRecordId;
    }

    public void setNextRecordId(int nextRecordId) {
        this.nextRecordId = Math.max(1, nextRecordId);
    }

    public int getNextPaymentId() {
        return nextPaymentId;
    }

    public void setNextPaymentId(int nextPaymentId) {
        this.nextPaymentId = Math.max(1, nextPaymentId);
    }
}
