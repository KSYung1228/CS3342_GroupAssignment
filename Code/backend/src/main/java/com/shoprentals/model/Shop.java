package com.shoprentals.model;

import java.util.List;

/**
 * Represents a physical shop unit inside a building.
 * gridRow/gridCol give its position in the floor plan grid displayed in the UI.
 * imageUrl stores an optional photo of the shop.
 */
public class Shop {
    private String id;
    private String buildingId;
    private String number;      // e.g. "A01"
    private double area;        // sq ft
    private int floor;
    private double baseRent;    // per month before ratio applied
    private String location;    // description
    private String shopTypeId;
    private List<String> facilities;
    private String complianceStatus; // COMPLIANT, NON_COMPLIANT, PENDING
    private String status;           // AVAILABLE, OCCUPIED, MAINTENANCE

    // Grid position on the floor plan
    private int gridRow;
    private int gridCol;

    // Optional shop photo URL (relative to frontend/images/)
    private String imageUrl;

    public Shop() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBuildingId() { return buildingId; }
    public void setBuildingId(String buildingId) { this.buildingId = buildingId; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public double getArea() { return area; }
    public void setArea(double area) { this.area = area; }

    public int getFloor() { return floor; }
    public void setFloor(int floor) { this.floor = floor; }

    public double getBaseRent() { return baseRent; }
    public void setBaseRent(double baseRent) { this.baseRent = baseRent; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getShopTypeId() { return shopTypeId; }
    public void setShopTypeId(String shopTypeId) { this.shopTypeId = shopTypeId; }

    public List<String> getFacilities() { return facilities; }
    public void setFacilities(List<String> facilities) { this.facilities = facilities; }

    public String getComplianceStatus() { return complianceStatus; }
    public void setComplianceStatus(String complianceStatus) { this.complianceStatus = complianceStatus; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getGridRow() { return gridRow; }
    public void setGridRow(int gridRow) { this.gridRow = gridRow; }

    public int getGridCol() { return gridCol; }
    public void setGridCol(int gridCol) { this.gridCol = gridCol; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
