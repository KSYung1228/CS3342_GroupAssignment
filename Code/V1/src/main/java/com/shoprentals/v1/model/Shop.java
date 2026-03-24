package com.shoprentals.v1.model;

public class Shop {
    private final int shopId;
    private String shopNum;
    private ShopStatus status;
    private double area;
    private ShopType type;

    public Shop(int shopId, String shopNum, ShopStatus status, double area, ShopType type) {
        this.shopId = shopId;
        this.shopNum = shopNum;
        this.status = status;
        this.area = area;
        this.type = type;
    }

    public Shop(int shopId, String shopNum, ShopStatus status, double area) {
        this(shopId, shopNum, status, area, ShopType.SOLID);
    }

    public void updateStoreStatus(ShopStatus newStatus) {
        this.status = newStatus;
    }

    public void editStoreInfo(String newShopNum, double newArea) {
        this.shopNum = newShopNum;
        this.area = newArea;
    }

    public void editStoreInfo(String newShopNum, double newArea, ShopType newType) {
        this.shopNum = newShopNum;
        this.area = newArea;
        this.type = newType;
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

    public ShopType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "Shop{" +
                "shopId=" + shopId +
                ", shopNum='" + shopNum + '\'' +
                ", status=" + status +
                ", area=" + area +
                ", type=" + type +
                '}';
    }
}
