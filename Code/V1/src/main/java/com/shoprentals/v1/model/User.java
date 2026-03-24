package com.shoprentals.v1.model;

public abstract class User {
    private final String userId;
    private final String username;
    private String password;

    protected User(String userId, String username, String password) {
        this.userId = userId;
        this.username = username;
        this.password = password;
    }

    public boolean login(String inputPassword) {
        return password.equals(inputPassword);
    }

    public void changePassword(String newPassword) {
        this.password = newPassword;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }
}
