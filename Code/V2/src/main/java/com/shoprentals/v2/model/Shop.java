package com.shoprentals.v2.model;

import java.util.ArrayList;
import java.util.List;

public class Shop {
    private final int shopId;
    private String shopNum;
    private ShopStatus status;
    private double area;
    private ShopType type;
    private String floor; // e.g. "G", "1F", "2F"

    private int posX;
    private int posY;
    private int width;
    private int height;

    /**
     * Explicit cell list for arbitrary shapes, e.g. ["1,2","1,3","2,2"].
     * When non-empty this takes precedence over the bounding box for rendering.
     */
    private List<String> cells;

    public Shop(int shopId, String shopNum, ShopStatus status, double area, ShopType type,
                int posX, int posY, int width, int height) {
        this(shopId, shopNum, status, area, type, posX, posY, width, height, "G");
    }

    public Shop(int shopId, String shopNum, ShopStatus status, double area, ShopType type,
                int posX, int posY, int width, int height, String floor) {
        this.shopId = shopId;
        this.shopNum = shopNum;
        this.status = status;
        this.area = area;
        this.type = type;
        this.posX = posX;
        this.posY = posY;
        this.width = width;
        this.height = height;
        this.floor = (floor == null || floor.isBlank()) ? "G" : floor;
        this.cells = new ArrayList<>();
    }

    public Shop(int shopId, String shopNum, ShopStatus status, double area, ShopType type) {
        this(shopId, shopNum, status, area, type, 0, 0, 2, 2, "G");
    }

    public void updateStoreStatus(ShopStatus newStatus) { this.status = newStatus; }

    public void editStoreInfo(String newShopNum, double newArea, ShopType newType) {
        this.shopNum = newShopNum;
        this.area = newArea;
        this.type = newType;
    }

    public void setFloor(String floor) {
        this.floor = (floor == null || floor.isBlank()) ? "G" : floor;
    }

    public void setPosition(int posX, int posY, int width, int height) {
        this.posX = posX;
        this.posY = posY;
        this.width = width;
        this.height = height;
        this.cells = new ArrayList<>();
    }

    public void setCells(List<String> cells) {
        this.cells = new ArrayList<>(cells);
        if (!cells.isEmpty()) recomputeBoundingBox();
    }

    private void recomputeBoundingBox() {
        int minC = Integer.MAX_VALUE, maxC = Integer.MIN_VALUE;
        int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;
        for (String key : cells) {
            String[] parts = key.split(",");
            int c = Integer.parseInt(parts[0]);
            int r = Integer.parseInt(parts[1]);
            minC = Math.min(minC, c); maxC = Math.max(maxC, c);
            minR = Math.min(minR, r); maxR = Math.max(maxR, r);
        }
        this.posX   = minC;
        this.posY   = minR;
        this.width  = maxC - minC + 1;
        this.height = maxR - minR + 1;
    }

    public boolean hasCells() { return cells != null && !cells.isEmpty(); }

    public int getShopId()        { return shopId; }
    public String getShopNum()    { return shopNum; }
    public ShopStatus getStatus() { return status; }
    public double getArea()       { return area; }
    public ShopType getType()     { return type; }
    public String getFloor()      { return floor; }
    public int getPosX()          { return posX; }
    public int getPosY()          { return posY; }
    public int getWidth()         { return width; }
    public int getHeight()        { return height; }
    public List<String> getCells(){ return cells != null ? cells : new ArrayList<>(); }

    @Override
    public String toString() {
        return "Shop{id=" + shopId + ", num='" + shopNum + "', floor=" + floor
                + ", status=" + status + ", area=" + area + ", type=" + type + "}";
    }
}
