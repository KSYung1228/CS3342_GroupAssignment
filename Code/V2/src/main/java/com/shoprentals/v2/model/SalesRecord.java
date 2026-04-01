package com.shoprentals.v2.model;

import com.shoprentals.v2.pattern.strategy.CommissionStrategy;

public class SalesRecord {
    private final int recordId;
    private final String month;
    private final double totalSales;
    private double commissionAmount;

    public SalesRecord(int recordId, String month, double totalSales) {
        this.recordId = recordId;
        this.month = month;
        this.totalSales = totalSales;
    }

    public void calculateCommission(CommissionStrategy strategy, double commissionRate) {
        this.commissionAmount = strategy.calculateCommission(totalSales, commissionRate);
    }

    public int getRecordId() { return recordId; }
    public String getMonth() { return month; }
    public double getTotalSales() { return totalSales; }
    public double getCommissionAmount() { return commissionAmount; }
}
