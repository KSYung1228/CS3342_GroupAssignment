package com.shoprentals.v2.model;

import com.shoprentals.v2.service.ShopRentalService;

public class ContractManager extends User {
    public ContractManager(String userId, String username, String password) {
        super(userId, username, password);
    }

    public boolean permitRequest(LeaseContract contract) {
        return contract.getStatus() == ContractStatus.PENDING_APPROVAL;
    }

    public void applyApproveContractService(LeaseContract contract, ShopRentalService service) {
        service.approveContract(contract);
    }

    public void manageAllStore(Shop shop, ShopStatus status) {
        shop.updateStoreStatus(status);
    }
}
