package model;

import service.RentalService;
import java.util.Scanner;

public class Tenant extends User {
    public String contactPerson;
    public String logo;

    // 將「申請租賃」的邏輯封裝在 Tenant 類別
    public void requestRentStore(RentalService service, Scanner scanner) {
        System.out.println("\n--- 商店網格狀態 ---");
        System.out.printf("%-5s | %-10s | %-8s | %-10s%n", "ID", "編號", "月租", "狀態");
        service.shopMap.values().forEach(s -> {
            String statusDisplay = s.status.equals(Shop.HOLD) ? "!! HOLD !!" : s.status;
            System.out.printf("%-5d | %-10s | %-8.1f | %-10s%n", s.shopId, s.shopNum, s.monthlyRent, statusDisplay);
        });

        System.out.print("輸入 Shop ID 申請 (0 返回): ");
        String id = scanner.next();
        if (id.equals("0")) return;

        Shop s = service.findShop(id);
        if (s != null && s.status.equals(Shop.OPEN)) {
            LeaseContract lc = new LeaseContract(5000 + s.shopId, s.shopNum, s.monthlyRent);
            service.contractMap.put(String.valueOf(lc.contractId), lc);
            s.status = Shop.HOLD;
            System.out.println(">>> [OOP 執行] 租客已發送申請，店舖進入 HOLD。");
        } else {
            System.out.println("!!! 無法申請。");
        }
    }

    public void signContract(RentalService service, Scanner scanner) {
        System.out.println("\n--- 待支付合約 ---");
        service.contractMap.values().stream()
            .filter(c -> c.status.equals(LeaseContract.WAITING_PAYMENT))
            .forEach(c -> System.out.println("合約 ID: " + c.contractId + " | 金額: " + c.amountToPay));
        
        System.out.print("輸入合約 ID 支付 (0 返回): ");
        String cid = scanner.next();
        if (service.contractMap.containsKey(cid)) {
            service.contractMap.get(cid).status = LeaseContract.SIGNED;
            System.out.println(">>> [OOP 執行] 租客已支付完成。");
        }
    }
}