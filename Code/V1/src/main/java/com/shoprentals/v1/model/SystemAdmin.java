package com.shoprentals.v1.model;

import java.util.HashMap;
import java.util.Map;

public class SystemAdmin extends User {
    private final Map<String, String> userPermissions = new HashMap<>();

    public SystemAdmin(String userId, String username, String password) {
        super(userId, username, password);
    }

    public User createAccount(String role, String userId, String username, String password) {
        return switch (role.toLowerCase()) {
            case "accounting" -> new Accounting(userId, username, password);
            case "floor" -> new Floor(userId, username, password, 1);
            case "tenant" -> new Tenant(userId, username, password, username + " contact");
            case "contractmanager" -> new ContractManager(userId, username, password);
            default -> throw new IllegalArgumentException("Unsupported role: " + role);
        };
    }

    public void changeUserPermission(String userId, String permission) {
        userPermissions.put(userId, permission);
    }

    public String getPermission(String userId) {
        return userPermissions.getOrDefault(userId, "NONE");
    }

    public Map<String, String> getUserPermissionsSnapshot() {
        return new HashMap<>(userPermissions);
    }

    public void replaceUserPermissions(Map<String, String> permissions) {
        userPermissions.clear();
        userPermissions.putAll(permissions);
    }
}
