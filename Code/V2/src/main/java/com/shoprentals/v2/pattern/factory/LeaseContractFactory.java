package com.shoprentals.v2.pattern.factory;

import com.shoprentals.v2.model.LeaseContract;
import com.shoprentals.v2.model.Shop;
import com.shoprentals.v2.model.Tenant;

public interface LeaseContractFactory {
    LeaseContract createContract(int contractId, Shop shop, Tenant tenant, double baseRent);
}
