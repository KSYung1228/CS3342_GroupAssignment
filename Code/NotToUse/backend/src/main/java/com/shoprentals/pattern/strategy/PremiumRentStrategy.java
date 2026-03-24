package com.shoprentals.pattern.strategy;

import com.shoprentals.model.Shop;
import com.shoprentals.model.ShopType;

/**
 * Premium rent: increases rent for high-status/high-traffic stores.
 * Accounting can raise rent when store status is excellent.
 */
public class PremiumRentStrategy implements RentCalculationStrategy {
    private final double surchargeRate; // e.g. 0.15 for 15% premium

    public PremiumRentStrategy(double surchargeRate) {
        this.surchargeRate = surchargeRate;
    }

    @Override
    public double calculate(Shop shop, ShopType shopType) {
        double standard = shop.getBaseRent() * shopType.getRentRatio();
        return standard * (1.0 + surchargeRate);
    }
}
