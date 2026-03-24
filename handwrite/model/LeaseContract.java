package model;

public class LeaseContract {
    // 狀態流：Pending (待審核) -> WaitingPayment (待支付) -> Signed (已支付) -> Active (會計已確認)
    public static final String PENDING = "Pending";
    public static final String WAITING_PAYMENT = "WaitingPayment";
    public static final String SIGNED = "Signed"; 
    public static final String ACTIVE = "Active";

    public int contractId;
    public String status; 
    public String targetShopNum;
    public double amountToPay;

    public LeaseContract(int id, String shopNum, double rent) {
        this.contractId = id;
        this.targetShopNum = shopNum;
        this.amountToPay = rent;
        this.status = PENDING;
    }
}