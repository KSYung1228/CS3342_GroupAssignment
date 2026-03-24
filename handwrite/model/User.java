package model;

public abstract class User {
    public int userId;
    public String username;
    public String password;

    public boolean login() {
        return true; 
    }

    public void changePassword() {
        System.out.println("Password changed.");
    }
}