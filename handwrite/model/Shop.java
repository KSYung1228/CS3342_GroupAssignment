package model;

public class Shop {
    public static final String OPEN = "Open";
    public static final String HOLD = "HOLD"; // 新增：已被申請但未支付
    public static final String CLOSED = "Closed"; // 正式租出

    public int shopId;
    public String shopNum;
    public String status; 
    public double area;
    public double monthlyRent;

    public Shop(int id, String num, double area, double rent) {
        this.shopId = id;
        this.shopNum = num;
        this.area = area;
        this.monthlyRent = rent;
        this.status = OPEN;
    }
}