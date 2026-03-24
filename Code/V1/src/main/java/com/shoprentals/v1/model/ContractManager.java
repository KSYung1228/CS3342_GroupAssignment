package com.shoprentals.v1.model;

import com.shoprentals.v1.service.ShopRentalService;

public class ContractManager extends User {
    public ContractManager(String userId, String username, String password) {
        super(userId, username, password);
    }

    public boolean permitRequest(LeaseContract contract) {
        return contract.getStatus() == ContractStatus.PENDING_APPROVAL;
    }

    public void createContract(LeaseContract contract, ShopRentalService service) {
        service.approveContract(contract);
    }

    public void manageAllStore(Shop shop, ShopStatus status) {
        shop.updateStoreStatus(status);
    }

    public void mergeOrSplitStorePosition() {
        System.out.println("Contract manager executed merge/split store position operation.");
    }
}
