package com.shoprentals.v1.pattern.strategy;

public interface CommissionStrategy {
    double calculateCommission(double totalSales, double baseRate);
}
