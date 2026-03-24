package com.shoprentals.pattern.strategy;

import com.shoprentals.model.Shop;
import com.shoprentals.model.ShopType;

/**
 * Promotional rent: applies a percentage discount on top of standard rent.
 * Used by Accounting to adjust rent for stores with lower store status.
 */
public class PromotionalRentStrategy implements RentCalculationStrategy {
    private final double discountRate; // e.g. 0.20 for 20% off

    public PromotionalRentStrategy(double discountRate) {
        this.discountRate = discountRate;
    }

    @Override
    public double calculate(Shop shop, ShopType shopType) {
        double standard = shop.getBaseRent() * shopType.getRentRatio();
        return standard * (1.0 - discountRate);
    }
}
