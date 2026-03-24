package com.shoprentals.v1.pattern.factory;

import com.shoprentals.v1.model.LeaseContract;
import com.shoprentals.v1.model.Shop;
import com.shoprentals.v1.model.Tenant;

public class ShortTermLeaseFactory implements LeaseContractFactory {
    private static final double SHORT_TERM_COMMISSION_RATE = 0.12;

    @Override
    public LeaseContract createContract(int contractId, Shop shop, Tenant tenant, double baseRent) {
        return new LeaseContract(contractId, shop, tenant, baseRent * 0.9, SHORT_TERM_COMMISSION_RATE);
    }
}
