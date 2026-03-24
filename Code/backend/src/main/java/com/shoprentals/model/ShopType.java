package com.shoprentals.model;

import java.util.List;

/**
 * Defines a category of shop (e.g. F&B, Retail, Studio).
 * Holds rent ratio and restrictions used by the Strategy pattern for rent calculation.
 */
public class ShopType {
    private String id;
    private String typeName;
    private String description;
    private double rentRatio;      // multiplier applied to base rent
    private List<String> restrictions;   // e.g. "no open flame"
    private List<String> facilityReqs;   // e.g. "ventilation system"

    public ShopType() {}

    public ShopType(String id, String typeName, String description, double rentRatio,
                    List<String> restrictions, List<String> facilityReqs) {
        this.id = id;
        this.typeName = typeName;
        this.description = description;
        this.rentRatio = rentRatio;
        this.restrictions = restrictions;
        this.facilityReqs = facilityReqs;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getRentRatio() { return rentRatio; }
    public void setRentRatio(double rentRatio) { this.rentRatio = rentRatio; }

    public List<String> getRestrictions() { return restrictions; }
    public void setRestrictions(List<String> restrictions) { this.restrictions = restrictions; }

    public List<String> getFacilityReqs() { return facilityReqs; }
    public void setFacilityReqs(List<String> facilityReqs) { this.facilityReqs = facilityReqs; }
}
