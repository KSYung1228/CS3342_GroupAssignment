package model;

public class SalesRecord {
    public int recordId;
    public String month;
    public double totalSales;
    public double commissionAmount;

    public void calculateCommission() {
        this.commissionAmount = totalSales * 0.05; // 範例邏輯
    }
}