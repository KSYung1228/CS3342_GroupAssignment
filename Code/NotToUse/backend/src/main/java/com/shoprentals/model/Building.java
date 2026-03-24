package com.shoprentals.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents a commercial building containing multiple shops.
 */
public class Building {
    private String id;
    private String name;
    private String address;
    private int totalFloors;
    @JsonProperty("allowedShopTypes")
    private List<String> allowedShopTypeIds;
    private String status; // ACTIVE, INACTIVE

    // Grid layout: rows x columns per floor
    private int gridRows;
    private int gridCols;

    public Building() {}

    public Building(String id, String name, String address, int totalFloors,
                    List<String> allowedShopTypeIds, String status, int gridRows, int gridCols) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.totalFloors = totalFloors;
        this.allowedShopTypeIds = allowedShopTypeIds;
        this.status = status;
        this.gridRows = gridRows;
        this.gridCols = gridCols;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getTotalFloors() { return totalFloors; }
    public void setTotalFloors(int totalFloors) { this.totalFloors = totalFloors; }

    public List<String> getAllowedShopTypeIds() { return allowedShopTypeIds; }
    public void setAllowedShopTypeIds(List<String> allowedShopTypeIds) { this.allowedShopTypeIds = allowedShopTypeIds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getGridRows() { return gridRows; }
    public void setGridRows(int gridRows) { this.gridRows = gridRows; }

    public int getGridCols() { return gridCols; }
    public void setGridCols(int gridCols) { this.gridCols = gridCols; }

    public boolean isShopTypeAllowed(String shopTypeId) {
        return allowedShopTypeIds != null && allowedShopTypeIds.contains(shopTypeId);
    }
}
