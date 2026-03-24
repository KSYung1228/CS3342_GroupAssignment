package com.shoprentals.v1.model;

public class Shop {
    private final int shopId;
    private String shopNum;
    private ShopStatus status;
    private double area;

    public Shop(int shopId, String shopNum, ShopStatus status, double area) {
        this.shopId = shopId;
        this.shopNum = shopNum;
        this.status = status;
        this.area = area;
    }

    public void updateStoreStatus(ShopStatus newStatus) {
        this.status = newStatus;
    }

    public void editStoreInfo(String newShopNum, double newArea) {
        this.shopNum = newShopNum;
        this.area = newArea;
    }

    public int getShopId() {
        return shopId;
    }

    public String getShopNum() {
        return shopNum;
    }

    public ShopStatus getStatus() {
        return status;
    }

    public double getArea() {
        return area;
    }

    @Override
    public String toString() {
        return "Shop{" +
                "shopId=" + shopId +
                ", shopNum='" + shopNum + '\'' +
                ", status=" + status +
                ", area=" + area +
                '}';
    }
}
