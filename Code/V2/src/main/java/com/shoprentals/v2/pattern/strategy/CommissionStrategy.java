package com.shoprentals.v2.pattern.strategy;

public interface CommissionStrategy {
    double calculateCommission(double totalSales, double baseRate);
}
