package com.shoprentals.v1.pattern.factory;

import com.shoprentals.v1.model.LeaseContract;
import com.shoprentals.v1.model.Shop;
import com.shoprentals.v1.model.Tenant;

public interface LeaseContractFactory {
    LeaseContract createContract(int contractId, Shop shop, Tenant tenant, double baseRent);
}
