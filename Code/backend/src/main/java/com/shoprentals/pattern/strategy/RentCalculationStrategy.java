package com.shoprentals.pattern.strategy;

import com.shoprentals.model.Shop;
import com.shoprentals.model.ShopType;

/**
 * Strategy pattern: different algorithms for calculating monthly rent.
 */
public interface RentCalculationStrategy {
    /**
     * @param shop     the shop being leased
     * @param shopType the shop's type (holds rentRatio)
     * @return calculated monthly rent in HKD
     */
    double calculate(Shop shop, ShopType shopType);
}
