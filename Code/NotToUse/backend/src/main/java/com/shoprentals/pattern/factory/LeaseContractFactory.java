package com.shoprentals.pattern.factory;

import com.shoprentals.model.LeaseContract;
import com.shoprentals.model.Shop;
import com.shoprentals.model.ShopType;
import com.shoprentals.pattern.strategy.RentCalculationStrategy;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Factory Method pattern: subclasses override createContract() to produce
 * different contract flavours (standard, short-term, renewal).
 */
public abstract class LeaseContractFactory {

    /** Template method: builds and returns a configured LeaseContract. */
    public final LeaseContract create(String merchantId, Shop shop, ShopType shopType,
                                      LocalDate start, LocalDate end,
                                      RentCalculationStrategy strategy) {
        LeaseContract contract = createContract();
        contract.setId(UUID.randomUUID().toString());
        contract.setMerchantId(merchantId);
        contract.setShopId(shop.getId());
        contract.setStartTime(start);
        contract.setEndTime(end);
        contract.setRentPerMonth(strategy.calculate(shop, shopType));
        contract.setDeposit(contract.getRentPerMonth() * depositMonths());
        contract.setContractState("DRAFT");
        contract.setStatus("DRAFT");
        configure(contract, shop, shopType);
        return contract;
    }

    /** Subclasses instantiate and set type-specific defaults. */
    protected abstract LeaseContract createContract();

    /** Number of months' rent required as deposit. */
    protected abstract int depositMonths();

    /** Hook for additional configuration. */
    protected void configure(LeaseContract contract, Shop shop, ShopType shopType) {}
}
