package com.shoprentals.pattern.strategy;

import com.shoprentals.model.Shop;
import com.shoprentals.model.ShopType;

/**
 * Standard rent: baseRent * rentRatio.
 */
public class StandardRentStrategy implements RentCalculationStrategy {
    @Override
    public double calculate(Shop shop, ShopType shopType) {
        return shop.getBaseRent() * shopType.getRentRatio();
    }
}
