package app;

import service.RentalService;
import model.*;
import java.util.Scanner;

public class MainApp {
    private static RentalService service = new RentalService();
    private static Scanner scanner = new Scanner(System.in);
    private static User currentUser;
    private static boolean isLoggedOut = true;

    public static void main(String[] args) {
        while (true) {
            if (isLoggedOut) login();
            else runRoleMenu();
        }
    }

    private static void login() {
        System.out.print("\n登入 (tenant/manager/accountant) 或 exit: ");
        String login = scanner.next().toLowerCase();
        if (login.equals("exit")) System.exit(0);
        currentUser = service.userMap.get(login);
        if (currentUser != null) isLoggedOut = false;
    }

    private static void runRoleMenu() {
        System.out.println("\n當前用戶: " + currentUser.username);
        if (currentUser instanceof Tenant) {
            Tenant t = (Tenant) currentUser;
            System.out.println("1. 申請租賃(含網格) 2. 支付合約 3. 登出");
            String opt = scanner.next();
            if (opt.equals("1")) t.requestRentStore(service, scanner);
            else if (opt.equals("2")) t.signContract(service, scanner);
            else if (opt.equals("3")) logout();
        } 
        else if (currentUser instanceof ContractManager) {
            ContractManager m = (ContractManager) currentUser;
            System.out.println("1. 審核申請 2. 登出");
            String opt = scanner.next();
            if (opt.equals("1")) m.permitRequest(service, scanner);
            else if (opt.equals("2")) logout();
        } 
        else if (currentUser instanceof Accounting) {
            Accounting a = (Accounting) currentUser;
            System.out.println("1. 確認收款 2. 登出");
            String opt = scanner.next();
            if (opt.equals("1")) a.confirmPaymentTransfer(service, scanner);
            else if (opt.equals("2")) logout();
        }
    }

    private static void logout() {
        isLoggedOut = true;
        currentUser = null;
        System.out.println("已登出。");
    }
}