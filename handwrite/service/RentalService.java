package service;

import model.*;
import java.util.*;

public class RentalService {
    public Map<String, Shop> shopMap = new LinkedHashMap<>();
    public Map<String, LeaseContract> contractMap = new HashMap<>();
    public Map<String, User> userMap = new HashMap<>();

    public RentalService() {
        shopMap.put("A-01", new Shop(101, "A-01", 120.0, 5000));
        shopMap.put("A-02", new Shop(102, "A-02", 80.0, 3000));
        
        userMap.put("tenant", createTenant("Tenant_User"));
        userMap.put("manager", createManager("Manager_User"));
        userMap.put("accountant", createAccounting("Accountant_User"));
    }

    private Tenant createTenant(String name) { Tenant t = new Tenant(); t.username = name; return t; }
    private ContractManager createManager(String name) { ContractManager m = new ContractManager(); m.username = name; return m; }
    private Accounting createAccounting(String name) { Accounting a = new Accounting(); a.username = name; return a; }

    public Shop findShop(String id) {
        for (Shop s : shopMap.values()) if (String.valueOf(s.shopId).equals(id)) return s;
        return null;
    }

    public Shop findShopByNum(String num) { return shopMap.get(num); }
}