package com.shoprentals.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shoprentals.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Singleton (Spring @Repository) that persists all data as a single JSON file.
 * Acts as a simple in-memory store backed by JSON for the demo.
 */
@Repository
public class DataRepository {

    @Value("${data.file.path}")
    private String dataFilePath;

    private final ObjectMapper mapper;

    // In-memory stores
    private Map<String, Building>      buildings    = new LinkedHashMap<>();
    private Map<String, ShopType>      shopTypes    = new LinkedHashMap<>();
    private Map<String, Shop>          shops        = new LinkedHashMap<>();
    private Map<String, Merchant>      merchants    = new LinkedHashMap<>();
    private Map<String, Admin>         admins       = new LinkedHashMap<>();
    private Map<String, LeaseContract> contracts    = new LinkedHashMap<>();
    private Map<String, PaymentRecord> payments     = new LinkedHashMap<>();

    public DataRepository() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void init() {
        File file = new File(dataFilePath);
        if (file.exists()) {
            load(file);
        } else {
            file.getParentFile().mkdirs();
            seedDemoData();
            save();
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void load(File file) {
        try {
            Map<String, Object> root = mapper.readValue(file, Map.class);
            buildings = loadMap(root, "buildings", Building.class);
            shopTypes = loadMap(root, "shopTypes", ShopType.class);
            shops     = loadMap(root, "shops",     Shop.class);
            merchants = loadMap(root, "merchants", Merchant.class);
            admins    = loadMap(root, "admins",    Admin.class);
            contracts = loadMap(root, "contracts", LeaseContract.class);
            payments  = loadMap(root, "payments",  PaymentRecord.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load data from " + dataFilePath, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, T> loadMap(Map<String, Object> root, String key, Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>();
        Object raw = root.get(key);
        if (raw instanceof Map<?,?> m) {
            for (Map.Entry<?,?> e : m.entrySet()) {
                result.put((String) e.getKey(),
                    mapper.convertValue(e.getValue(), type));
            }
        }
        return result;
    }

    public synchronized void save() {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("buildings", buildings);
            root.put("shopTypes", shopTypes);
            root.put("shops",     shops);
            root.put("merchants", merchants);
            root.put("admins",    admins);
            root.put("contracts", contracts);
            root.put("payments",  payments);
            mapper.writeValue(new File(dataFilePath), root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save data to " + dataFilePath, e);
        }
    }

    // ── CRUD helpers ───────────────────────────────────────────────────────────

    public Map<String, Building>      getBuildings()  { return buildings; }
    public Map<String, ShopType>      getShopTypes()  { return shopTypes; }
    public Map<String, Shop>          getShops()      { return shops; }
    public Map<String, Merchant>      getMerchants()  { return merchants; }
    public Map<String, Admin>         getAdmins()     { return admins; }
    public Map<String, LeaseContract> getContracts()  { return contracts; }
    public Map<String, PaymentRecord> getPayments()   { return payments; }

    // ── Demo seed data ─────────────────────────────────────────────────────────

    private void seedDemoData() {
        // Admin accounts
        Admin sysAdmin = new Admin();
        sysAdmin.setId("admin-001");
        sysAdmin.setUsername("admin");
        sysAdmin.setPasswordHash("admin123");   // plain text for demo only
        sysAdmin.setRole("SYSTEM_ADMIN");
        sysAdmin.setStatus("ACTIVE");
        admins.put(sysAdmin.getId(), sysAdmin);

        Admin accounting = new Admin();
        accounting.setId("admin-002");
        accounting.setUsername("accounting");
        accounting.setPasswordHash("acc123");
        accounting.setRole("ACCOUNTING");
        accounting.setStatus("ACTIVE");
        admins.put(accounting.getId(), accounting);

        Admin contractMgr = new Admin();
        contractMgr.setId("admin-003");
        contractMgr.setUsername("contract");
        contractMgr.setPasswordHash("con123");
        contractMgr.setRole("CONTRACT_MANAGER");
        contractMgr.setStatus("ACTIVE");
        admins.put(contractMgr.getId(), contractMgr);

        // Shop Types
        ShopType retail = new ShopType("type-001", "Retail",
            "General retail shop", 1.0,
            List.of(), List.of("display shelving"));
        ShopType fb = new ShopType("type-002", "F&B",
            "Food and Beverage outlet", 1.3,
            List.of("no open flame without permit"), List.of("ventilation system", "grease trap"));
        ShopType studio = new ShopType("type-003", "Studio",
            "Office or creative studio", 0.9,
            List.of(), List.of("network cabling"));
        shopTypes.put(retail.getId(), retail);
        shopTypes.put(fb.getId(), fb);
        shopTypes.put(studio.getId(), studio);

        // Building
        Building b = new Building("bld-001", "Harbour Plaza",
            "1 Harbour Road, Wanchai, Hong Kong", 5,
            List.of("type-001", "type-002", "type-003"), "ACTIVE", 4, 5);
        buildings.put(b.getId(), b);

        // Shops – placed on a 4×5 grid across 2 floors
        addShop("shop-001", "bld-001", "A01", 350, 2, 18000, "Ground Floor West",  "type-001", List.of("display shelving"),            "COMPLIANT", "AVAILABLE", 0, 0, null);
        addShop("shop-002", "bld-001", "A02", 420, 2, 22000, "Ground Floor Centre","type-002", List.of("ventilation system","grease trap"),"COMPLIANT","AVAILABLE",0,1,null);
        addShop("shop-003", "bld-001", "A03", 280, 2, 15000, "Ground Floor East",  "type-001", List.of("display shelving"),            "COMPLIANT", "OCCUPIED", 0, 2, null);
        addShop("shop-004", "bld-001", "A04", 500, 2, 28000, "Ground Floor Corner","type-002", List.of("ventilation system","grease trap"),"COMPLIANT","AVAILABLE",0,3,null);
        addShop("shop-005", "bld-001", "B01", 310, 3, 19000, "First Floor West",   "type-001", List.of("display shelving"),            "COMPLIANT", "AVAILABLE", 1, 0, null);
        addShop("shop-006", "bld-001", "B02", 390, 3, 21000, "First Floor Centre", "type-003", List.of("network cabling"),             "COMPLIANT", "AVAILABLE", 1, 1, null);
        addShop("shop-007", "bld-001", "B03", 460, 3, 25000, "First Floor East",   "type-002", List.of("ventilation system"),          "NON_COMPLIANT","MAINTENANCE",1,2,null);
        addShop("shop-008", "bld-001", "B04", 320, 3, 17000, "First Floor Corner", "type-001", List.of("display shelving"),            "COMPLIANT", "AVAILABLE", 1, 3, null);

        // Merchant
        Merchant m = new Merchant();
        m.setId("mer-001");
        m.setName("Chan Tai Man Trading Ltd");
        m.setPhone("9123 4567");
        m.setEmail("chan@trading.hk");
        m.setBusinessLicense("BL-2024-00123");
        m.setStatus("APPROVED");
        m.setPasswordHash("mer123");
        merchants.put(m.getId(), m);
    }

    private void addShop(String id, String buildingId, String number, double area, int floor,
                         double baseRent, String location, String shopTypeId,
                         List<String> facilities, String compliance, String status,
                         int row, int col, String imageUrl) {
        Shop s = new Shop();
        s.setId(id); s.setBuildingId(buildingId); s.setNumber(number);
        s.setArea(area); s.setFloor(floor); s.setBaseRent(baseRent);
        s.setLocation(location); s.setShopTypeId(shopTypeId);
        s.setFacilities(facilities); s.setComplianceStatus(compliance);
        s.setStatus(status); s.setGridRow(row); s.setGridCol(col);
        s.setImageUrl(imageUrl);
        shops.put(id, s);
    }
}
