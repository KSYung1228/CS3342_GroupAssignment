package com.shoprentals.v2.pattern.strategy;

public class ProgressiveCommissionStrategy implements CommissionStrategy {
    @Override
    public double calculateCommission(double totalSales, double baseRate) {
        if (totalSales <= 10_000) {
            return totalSales * baseRate;
        }
        if (totalSales <= 30_000) {
            return (10_000 * baseRate) + ((totalSales - 10_000) * (baseRate + 0.01));
        }
        return (10_000 * baseRate) + (20_000 * (baseRate + 0.01)) + ((totalSales - 30_000) * (baseRate + 0.02));
    }
}
