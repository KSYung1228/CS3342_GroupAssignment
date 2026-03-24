package com.shoprentals.v1.pattern.factory;

import com.shoprentals.v1.model.LeaseContract;
import com.shoprentals.v1.model.Shop;
import com.shoprentals.v1.model.Tenant;

public class StandardLeaseFactory implements LeaseContractFactory {
    private static final double STANDARD_COMMISSION_RATE = 0.08;

    @Override
    public LeaseContract createContract(int contractId, Shop shop, Tenant tenant, double baseRent) {
        return new LeaseContract(contractId, shop, tenant, baseRent, STANDARD_COMMISSION_RATE);
    }
}
