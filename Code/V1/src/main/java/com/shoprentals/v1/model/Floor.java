package com.shoprentals.v1.model;

public class Floor extends User {
    private final int floorId;

    public Floor(String userId, String username, String password, int floorId) {
        super(userId, username, password);
        this.floorId = floorId;
    }

    public void manageStorePositions() {
        System.out.println("Floor manager arranged store positions on floor " + floorId);
    }
}
