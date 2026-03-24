package model;

import service.RentalService;
import java.util.Scanner;

public class Accounting extends User {
    public void confirmPaymentTransfer(RentalService service, Scanner scanner) {
        System.out.println("\n--- 會計收款確認 ---");
        long count = service.contractMap.values().stream()
            .filter(c -> c.status.equals(LeaseContract.SIGNED))
            .peek(c -> System.out.println("待確認: " + c.contractId))
            .count();

        if (count == 0) return;

        System.out.print("輸入收款確認 ID: ");
        String cid = scanner.next();
        if (service.contractMap.containsKey(cid)) {
            LeaseContract lc = service.contractMap.get(cid);
            lc.status = LeaseContract.ACTIVE;
            
            // 透過 service 找到店舖並變更為 CLOSED
            Shop s = service.findShopByNum(lc.targetShopNum);
            if (s != null) s.status = Shop.CLOSED;
            System.out.println(">>> [OOP 執行] 會計已入帳，交易完成。");
        }
    }
}