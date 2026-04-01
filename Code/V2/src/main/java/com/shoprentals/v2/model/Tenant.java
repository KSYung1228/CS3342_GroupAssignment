package com.shoprentals.v2.model;

import com.shoprentals.v2.pattern.factory.LeaseContractFactory;
import com.shoprentals.v2.pattern.strategy.CommissionStrategy;
import com.shoprentals.v2.service.ShopRentalService;

import java.util.List;

public class Tenant extends User {
    private final String contactPerson;

    public Tenant(String userId, String username, String password, String contactPerson) {
        super(userId, username, password);
        this.contactPerson = contactPerson;
    }

    public List<Shop> searchOpenStore(List<Shop> shops, ShopRentalService service) {
        return service.findOpenShops(shops);
    }

    public LeaseContract requestRentStore(Shop shop, ShopRentalService service,
                                          LeaseContractFactory factory, double baseRent) {
        return service.createLeaseRequest(this, shop, factory, baseRent);
    }

    public void signContract(LeaseContract contract) {
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new IllegalStateException("Contract must be active before signing acknowledgment");
        }
    }

    public Payment uploadMonthlyRecord(LeaseContract contract, String month, double sales,
                                       CommissionStrategy strategy, ShopRentalService service) {
        return service.submitMonthlySales(contract, month, sales, strategy);
    }

    public String getContactPerson() { return contactPerson; }
}
