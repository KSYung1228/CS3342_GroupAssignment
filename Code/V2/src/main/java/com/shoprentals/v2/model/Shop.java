package com.shoprentals.v2.model;

public class Shop {
    private final int shopId;
    private String shopNum;
    private ShopStatus status;
    private double area;
    private ShopType type;
    // Floor plan position (grid coordinates)
    private int posX;
    private int posY;
    private int width;
    private int height;

    public Shop(int shopId, String shopNum, ShopStatus status, double area, ShopType type,
                int posX, int posY, int width, int height) {
        this.shopId = shopId;
        this.shopNum = shopNum;
        this.status = status;
        this.area = area;
        this.type = type;
        this.posX = posX;
        this.posY = posY;
        this.width = width;
        this.height = height;
    }

    public Shop(int shopId, String shopNum, ShopStatus status, double area, ShopType type) {
        this(shopId, shopNum, status, area, type, 0, 0, 2, 2);
    }

    public void updateStoreStatus(ShopStatus newStatus) { this.status = newStatus; }

    public void editStoreInfo(String newShopNum, double newArea, ShopType newType) {
        this.shopNum = newShopNum;
        this.area = newArea;
        this.type = newType;
    }

    public void setPosition(int posX, int posY, int width, int height) {
        this.posX = posX;
        this.posY = posY;
        this.width = width;
        this.height = height;
    }

    public int getShopId() { return shopId; }
    public String getShopNum() { return shopNum; }
    public ShopStatus getStatus() { return status; }
    public double getArea() { return area; }
    public ShopType getType() { return type; }
    public int getPosX() { return posX; }
    public int getPosY() { return posY; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    @Override
    public String toString() {
        return "Shop{id=" + shopId + ", num='" + shopNum + "', status=" + status + ", area=" + area + ", type=" + type + "}";
    }
}
