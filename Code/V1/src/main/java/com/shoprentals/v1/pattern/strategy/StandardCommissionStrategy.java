package com.shoprentals.v1.pattern.strategy;

public class StandardCommissionStrategy implements CommissionStrategy {
    @Override
    public double calculateCommission(double totalSales, double baseRate) {
        return totalSales * baseRate;
    }
}
