package model;

import service.RentalService;
import java.util.Scanner;

public class ContractManager extends User {
    public void permitRequest(RentalService service, Scanner scanner) {
        System.out.println("\n--- 管理員審核 ---");
        long count = service.contractMap.values().stream()
            .filter(c -> c.status.equals(LeaseContract.PENDING))
            .peek(c -> System.out.println("待核准: " + c.contractId + " (店舖: " + c.targetShopNum + ")"))
            .count();

        if (count == 0) return;

        System.out.print("輸入核准 ID (0 返回): ");
        String cid = scanner.next();
        if (service.contractMap.containsKey(cid)) {
            service.contractMap.get(cid).status = LeaseContract.WAITING_PAYMENT;
            System.out.println(">>> [OOP 執行] 經理已核准申請。");
        }
    }
}