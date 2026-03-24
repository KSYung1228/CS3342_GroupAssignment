package com.shoprentals.v1;

import com.shoprentals.v1.model.Accounting;
import com.shoprentals.v1.model.ContractStatus;
import com.shoprentals.v1.model.ContractManager;
import com.shoprentals.v1.model.LeaseContract;
import com.shoprentals.v1.model.Payment;
import com.shoprentals.v1.model.PaymentStatus;
import com.shoprentals.v1.model.Shop;
import com.shoprentals.v1.model.ShopStatus;
import com.shoprentals.v1.model.SystemAdmin;
import com.shoprentals.v1.model.Tenant;
import com.shoprentals.v1.model.User;
import com.shoprentals.v1.pattern.factory.LeaseContractFactory;
import com.shoprentals.v1.pattern.factory.StandardLeaseFactory;
import com.shoprentals.v1.pattern.observer.AuditLogListener;
import com.shoprentals.v1.pattern.observer.RentalEventBus;
import com.shoprentals.v1.pattern.strategy.CommissionStrategy;
import com.shoprentals.v1.pattern.strategy.ProgressiveCommissionStrategy;
import com.shoprentals.v1.service.ShopRentalService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

public class ShopRentalApplication {
    private static final Path STATE_FILE = Paths.get("data", "app-state.properties");

    private static class AppContext {
        private final ShopRentalService service;
        private final SystemAdmin systemAdmin;
        private final Accounting accounting;
        private final Tenant tenant;
        private final ContractManager manager;
        private final List<Shop> allShops;
        private final LeaseContractFactory leaseFactory;
        private final CommissionStrategy commissionStrategy;
        private final List<LeaseContract> contracts;
        private final List<Payment> payments;
        private final Map<Integer, Integer> paymentToContractId;
        private Integer activeContractId;
        private Integer activePaymentId;

        private AppContext(ShopRentalService service,
                           SystemAdmin systemAdmin,
                           Accounting accounting,
                           Tenant tenant,
                           ContractManager manager,
                           List<Shop> allShops,
                           LeaseContractFactory leaseFactory,
                           CommissionStrategy commissionStrategy) {
            this.service = service;
            this.systemAdmin = systemAdmin;
            this.accounting = accounting;
            this.tenant = tenant;
            this.manager = manager;
            this.allShops = allShops;
            this.leaseFactory = leaseFactory;
            this.commissionStrategy = commissionStrategy;
            this.contracts = new ArrayList<>();
            this.payments = new ArrayList<>();
            this.paymentToContractId = new HashMap<>();
        }
    }

    public static void main(String[] args) {
        RentalEventBus eventBus = new RentalEventBus();
        eventBus.subscribe(new AuditLogListener());

        ShopRentalService service = new ShopRentalService(eventBus);

        SystemAdmin systemAdmin = new SystemAdmin("U001", "admin", "admin123");
        Accounting accounting = (Accounting) systemAdmin.createAccount("accounting", "U100", "acct", "pass");
        Tenant tenant = (Tenant) systemAdmin.createAccount("tenant", "U200", "freshMart", "pass");
        ContractManager manager = (ContractManager) systemAdmin.createAccount("contractmanager", "U300", "cm", "pass");

        List<Shop> allShops = new ArrayList<>();
        allShops.add(new Shop(1, "A-101", ShopStatus.OPEN, 35.0));
        allShops.add(new Shop(2, "A-102", ShopStatus.CLOSED, 40.0));

        LeaseContractFactory leaseFactory = new StandardLeaseFactory();
        CommissionStrategy commissionStrategy = new ProgressiveCommissionStrategy();

        AppContext ctx = new AppContext(
                service,
                systemAdmin,
                accounting,
                tenant,
                manager,
                allShops,
                leaseFactory,
                commissionStrategy
        );

        loadStateQuietly(ctx);

        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                printLoginScreen();
                System.out.print("Username (or type exit): ");
                String username = scanner.nextLine().trim();

                if ("exit".equalsIgnoreCase(username) || "0".equals(username)) {
                    running = false;
                    break;
                }

                System.out.print("Password: ");
                String password = scanner.nextLine().trim();

                User user = authenticateAndResolveUser(ctx, username, password);
                if (user == null) {
                    System.out.println("Login failed.");
                    continue;
                }

                System.out.println("Login successful: " + username);
                if (user instanceof Tenant) {
                    runTenantMenu(scanner, ctx);
                } else if (user instanceof ContractManager) {
                    runContractManagerMenu(scanner, ctx);
                } else if (user instanceof Accounting) {
                    runAccountingMenu(scanner, ctx);
                } else if (user instanceof SystemAdmin) {
                    runAdminMenu(scanner, ctx);
                }
            }
        }

        saveStateQuietly(ctx);
        System.out.println("Application ended.");
    }

    private static void printLoginScreen() {
        System.out.println();
        System.out.println("===== Shop Rental System V1 =====");
        System.out.println("Login accounts:");
        System.out.println("- admin / admin123");
        System.out.println("- acct / pass");
        System.out.println("- freshMart / pass");
        System.out.println("- cm / pass");
    }

    private static User authenticateAndResolveUser(AppContext ctx, String username, String password) {
        if (ctx.systemAdmin.getUsername().equals(username) && ctx.systemAdmin.login(password)) {
            return ctx.systemAdmin;
        }
        if (ctx.accounting.getUsername().equals(username) && ctx.accounting.login(password)) {
            return ctx.accounting;
        }
        if (ctx.tenant.getUsername().equals(username) && ctx.tenant.login(password)) {
            return ctx.tenant;
        }
        if (ctx.manager.getUsername().equals(username) && ctx.manager.login(password)) {
            return ctx.manager;
        }
        return null;
    }

    private static void runTenantMenu(Scanner scanner, AppContext ctx) {
        boolean back = false;
        while (!back) {
            System.out.println();
            System.out.println("--- Tenant Menu ---");
            System.out.println("1. View open shops");
            System.out.println("2. Request lease contract");
            System.out.println("3. Select active contract");
            System.out.println("4. Upload monthly sales record");
            System.out.println("5. View all contracts/payments");
            System.out.println("0. Logout");
            System.out.print("Choose an option: ");
            String option = scanner.nextLine().trim();

            try {
                switch (option) {
                    case "1" -> printOpenShops(ctx.tenant, ctx.allShops, ctx.service);
                    case "2" -> requestLease(scanner, ctx);
                    case "3" -> selectActiveContract(scanner, ctx);
                    case "4" -> uploadMonthlyRecord(scanner, ctx);
                    case "5" -> printAllState(ctx);
                    case "0" -> back = true;
                    default -> System.out.println("Unknown option. Try again.");
                }
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid number.");
            } catch (Exception ex) {
                System.out.println("Operation failed: " + ex.getMessage());
            }
        }
    }

    private static void runContractManagerMenu(Scanner scanner, AppContext ctx) {
        boolean back = false;
        while (!back) {
            System.out.println();
            System.out.println("--- ContractManager Menu ---");
            System.out.println("1. List contracts");
            System.out.println("2. Select active contract");
            System.out.println("3. Approve active contract");
            System.out.println("4. Update shop status");
            System.out.println("5. View all contracts/payments");
            System.out.println("0. Logout");
            System.out.print("Choose an option: ");
            String option = scanner.nextLine().trim();

            try {
                switch (option) {
                    case "1" -> printContracts(ctx);
                    case "2" -> selectActiveContract(scanner, ctx);
                    case "3" -> approveActiveContract(ctx);
                    case "4" -> updateShopStatus(scanner, ctx);
                    case "5" -> printAllState(ctx);
                    case "0" -> back = true;
                    default -> System.out.println("Unknown option. Try again.");
                }
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid number.");
            } catch (Exception ex) {
                System.out.println("Operation failed: " + ex.getMessage());
            }
        }
    }

    private static void runAccountingMenu(Scanner scanner, AppContext ctx) {
        boolean back = false;
        while (!back) {
            System.out.println();
            System.out.println("--- Accounting Menu ---");
            System.out.println("1. List payments");
            System.out.println("2. Select active payment");
            System.out.println("3. Confirm active payment");
            System.out.println("4. Inspect all shops");
            System.out.println("5. View all contracts/payments");
            System.out.println("0. Logout");
            System.out.print("Choose an option: ");
            String option = scanner.nextLine().trim();

            try {
                switch (option) {
                    case "1" -> printPayments(ctx);
                    case "2" -> selectActivePayment(scanner, ctx);
                    case "3" -> confirmActivePayment(ctx);
                    case "4" -> inspectAllShops(ctx);
                    case "5" -> printAllState(ctx);
                    case "0" -> back = true;
                    default -> System.out.println("Unknown option. Try again.");
                }
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid number.");
            } catch (Exception ex) {
                System.out.println("Operation failed: " + ex.getMessage());
            }
        }
    }

    private static void runAdminMenu(Scanner scanner, AppContext ctx) {
        boolean back = false;
        while (!back) {
            System.out.println();
            System.out.println("--- SystemAdmin Menu ---");
            System.out.println("1. Change user permission");
            System.out.println("2. View a user permission");
            System.out.println("0. Logout");
            System.out.print("Choose an option: ");
            String option = scanner.nextLine().trim();

            switch (option) {
                case "1" -> {
                    System.out.print("Enter target userId: ");
                    String userId = scanner.nextLine().trim();
                    System.out.print("Enter permission text: ");
                    String permission = scanner.nextLine().trim();
                    ctx.systemAdmin.changeUserPermission(userId, permission);
                    saveStateQuietly(ctx);
                    System.out.println("Permission updated and saved.");
                }
                case "2" -> {
                    System.out.print("Enter userId: ");
                    String userId = scanner.nextLine().trim();
                    System.out.println("Permission: " + ctx.systemAdmin.getPermission(userId));
                }
                case "0" -> back = true;
                default -> System.out.println("Unknown option. Try again.");
            }
        }
    }

    private static void requestLease(Scanner scanner, AppContext ctx) {
        Shop selectedShop = chooseShop(scanner, ctx.allShops);
        if (selectedShop == null) {
            System.out.println("Invalid shop id.");
            return;
        }
        if (selectedShop.getStatus() != ShopStatus.OPEN) {
            System.out.println("Selected shop is not open.");
            return;
        }

        System.out.print("Enter base rent: ");
        double baseRent = Double.parseDouble(scanner.nextLine().trim());
        LeaseContract contract = ctx.tenant.requestRentStore(selectedShop, ctx.service, ctx.leaseFactory, baseRent);
        ctx.contracts.add(contract);
        ctx.activeContractId = contract.getContractId();
        saveStateQuietly(ctx);
        System.out.println("Contract request created. Contract ID: " + contract.getContractId());
    }

    private static void selectActiveContract(Scanner scanner, AppContext ctx) {
        if (ctx.contracts.isEmpty()) {
            System.out.println("No contracts available.");
            return;
        }
        printContracts(ctx);
        System.out.print("Enter contract id to activate: ");
        int contractId = Integer.parseInt(scanner.nextLine().trim());
        LeaseContract contract = findContractById(ctx, contractId);
        if (contract == null) {
            System.out.println("Contract not found.");
            return;
        }
        ctx.activeContractId = contractId;
        saveStateQuietly(ctx);
        System.out.println("Active contract set to ID: " + contractId);
    }

    private static void approveActiveContract(AppContext ctx) {
        LeaseContract contract = getActiveContract(ctx);
        if (contract == null) {
            System.out.println("No active contract. Select one first.");
            return;
        }
        if (!ctx.manager.permitRequest(contract)) {
            System.out.println("Active contract is not waiting for approval.");
            return;
        }

        ctx.manager.createContract(contract, ctx.service);
        ctx.tenant.signContract(contract);
        saveStateQuietly(ctx);
        System.out.println("Contract approved and signed. Status: " + contract.getStatus());
    }

    private static void uploadMonthlyRecord(Scanner scanner, AppContext ctx) {
        LeaseContract contract = getActiveContract(ctx);
        if (contract == null) {
            System.out.println("No active contract. Select one first.");
            return;
        }
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            System.out.println("Active contract is not active yet. Approve first.");
            return;
        }

        System.out.print("Enter month (e.g. 2026-03): ");
        String month = scanner.nextLine().trim();
        System.out.print("Enter total sales: ");
        double sales = Double.parseDouble(scanner.nextLine().trim());

        Payment payment = ctx.tenant.uploadMonthlyRecord(
                contract,
                month,
                sales,
                ctx.commissionStrategy,
                ctx.service
        );

        ctx.payments.add(payment);
        ctx.paymentToContractId.put(payment.getPaymentId(), contract.getContractId());
        ctx.activePaymentId = payment.getPaymentId();
        saveStateQuietly(ctx);
        System.out.println("Monthly record uploaded. Payment ID: " + payment.getPaymentId() + ", amount: " + payment.getAmount());
    }

    private static void selectActivePayment(Scanner scanner, AppContext ctx) {
        if (ctx.payments.isEmpty()) {
            System.out.println("No payments available.");
            return;
        }
        printPayments(ctx);
        System.out.print("Enter payment id to activate: ");
        int paymentId = Integer.parseInt(scanner.nextLine().trim());
        Payment payment = findPaymentById(ctx, paymentId);
        if (payment == null) {
            System.out.println("Payment not found.");
            return;
        }
        ctx.activePaymentId = paymentId;
        saveStateQuietly(ctx);
        System.out.println("Active payment set to ID: " + paymentId);
    }

    private static void confirmActivePayment(AppContext ctx) {
        Payment payment = getActivePayment(ctx);
        if (payment == null) {
            System.out.println("No active payment. Select one first.");
            return;
        }

        boolean confirmed = ctx.accounting.confirmPaymentTransfer(payment);
        saveStateQuietly(ctx);
        System.out.println("Payment " + payment.getPaymentId() + " confirmed: " + confirmed);
    }

    private static void inspectAllShops(AppContext ctx) {
        for (Shop shop : ctx.allShops) {
            ctx.accounting.inspectStoreStatus(shop);
        }
    }

    private static void updateShopStatus(Scanner scanner, AppContext ctx) {
        Shop selectedShop = chooseShop(scanner, ctx.allShops);
        if (selectedShop == null) {
            System.out.println("Invalid shop id.");
            return;
        }

        System.out.println("Choose status: 1=OPEN, 2=CLOSED, 3=UNDER_REPAIR");
        String statusOption = scanner.nextLine().trim();
        ShopStatus newStatus = switch (statusOption) {
            case "1" -> ShopStatus.OPEN;
            case "2" -> ShopStatus.CLOSED;
            case "3" -> ShopStatus.UNDER_REPAIR;
            default -> null;
        };

        if (newStatus == null) {
            System.out.println("Invalid status option.");
            return;
        }

        ctx.manager.manageAllStore(selectedShop, newStatus);

        for (LeaseContract contract : ctx.contracts) {
            if (contract.getShop().getShopId() == selectedShop.getShopId()) {
                ctx.accounting.alterRentByStoreStatus(contract, selectedShop);
            }
        }

        ctx.accounting.inspectStoreStatus(selectedShop);
        saveStateQuietly(ctx);
    }

    private static void printOpenShops(Tenant tenant, List<Shop> allShops, ShopRentalService service) {
        List<Shop> openShops = tenant.searchOpenStore(allShops, service);
        if (openShops.isEmpty()) {
            System.out.println("No open shops available.");
            return;
        }

        System.out.println("Open shops:");
        for (Shop shop : openShops) {
            System.out.println("- " + shop.getShopId() + " | " + shop.getShopNum() + " | area=" + shop.getArea());
        }
    }

    private static void printContracts(AppContext ctx) {
        if (ctx.contracts.isEmpty()) {
            System.out.println("Contracts: none");
            return;
        }
        System.out.println("Contracts:");
        for (LeaseContract contract : ctx.contracts) {
            String activeTag = (ctx.activeContractId != null && ctx.activeContractId == contract.getContractId()) ? " [ACTIVE]" : "";
            System.out.println("- ID=" + contract.getContractId()
                    + ", shop=" + contract.getShop().getShopNum()
                    + ", status=" + contract.getStatus()
                    + ", baseRent=" + contract.getBaseRent()
                    + activeTag);
        }
    }

    private static void printPayments(AppContext ctx) {
        if (ctx.payments.isEmpty()) {
            System.out.println("Payments: none");
            return;
        }
        System.out.println("Payments:");
        for (Payment payment : ctx.payments) {
            Integer contractId = ctx.paymentToContractId.get(payment.getPaymentId());
            String activeTag = (ctx.activePaymentId != null && ctx.activePaymentId == payment.getPaymentId()) ? " [ACTIVE]" : "";
            System.out.println("- ID=" + payment.getPaymentId()
                    + ", contractId=" + (contractId == null ? "N/A" : contractId)
                    + ", amount=" + payment.getAmount()
                    + ", status=" + payment.getStatus()
                    + activeTag);
        }
    }

    private static void printAllState(AppContext ctx) {
        printContracts(ctx);
        printPayments(ctx);
    }

    private static Shop chooseShop(Scanner scanner, List<Shop> shops) {
        System.out.println("Available shops:");
        for (Shop shop : shops) {
            System.out.println("- " + shop.getShopId() + " | " + shop.getShopNum() + " | status=" + shop.getStatus());
        }
        System.out.print("Enter shop id: ");
        int shopId = Integer.parseInt(scanner.nextLine().trim());

        for (Shop shop : shops) {
            if (shop.getShopId() == shopId) {
                return shop;
            }
        }
        return null;
    }

    private static LeaseContract findContractById(AppContext ctx, int contractId) {
        for (LeaseContract contract : ctx.contracts) {
            if (contract.getContractId() == contractId) {
                return contract;
            }
        }
        return null;
    }

    private static LeaseContract getActiveContract(AppContext ctx) {
        if (ctx.activeContractId == null) {
            return null;
        }
        return findContractById(ctx, ctx.activeContractId);
    }

    private static Payment findPaymentById(AppContext ctx, int paymentId) {
        for (Payment payment : ctx.payments) {
            if (payment.getPaymentId() == paymentId) {
                return payment;
            }
        }
        return null;
    }

    private static Payment getActivePayment(AppContext ctx) {
        if (ctx.activePaymentId == null) {
            return null;
        }
        return findPaymentById(ctx, ctx.activePaymentId);
    }

    private static void loadStateQuietly(AppContext ctx) {
        try {
            boolean loaded = loadState(ctx);
            if (loaded) {
                System.out.println("State loaded from " + STATE_FILE + ".");
            } else {
                System.out.println("No saved state found. Using default data.");
            }
        } catch (IOException ex) {
            System.out.println("No previous state loaded: " + ex.getMessage());
        }
    }

    private static void saveStateQuietly(AppContext ctx) {
        try {
            saveState(ctx);
        } catch (IOException ex) {
            System.out.println("Failed to save state: " + ex.getMessage());
        }
    }

    private static boolean loadState(AppContext ctx) throws IOException {
        if (!Files.exists(STATE_FILE)) {
            return false;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(STATE_FILE)) {
            props.load(in);
        }

        int shopCount = getIntProperty(props, "shop.count", 0);
        if (shopCount > 0) {
            ctx.allShops.clear();
            for (int i = 0; i < shopCount; i++) {
                int shopId = getIntProperty(props, "shop." + i + ".id", i + 1);
                String shopNum = props.getProperty("shop." + i + ".num", "S-" + shopId);
                ShopStatus status = ShopStatus.valueOf(props.getProperty("shop." + i + ".status", ShopStatus.OPEN.name()));
                double area = getDoubleProperty(props, "shop." + i + ".area", 30.0);
                ctx.allShops.add(new Shop(shopId, shopNum, status, area));
            }
        }

        ctx.contracts.clear();
        int contractCount = getIntProperty(props, "contract.count", 0);
        for (int i = 0; i < contractCount; i++) {
            int contractId = getIntProperty(props, "contract." + i + ".id", i + 1);
            int shopId = getIntProperty(props, "contract." + i + ".shopId", -1);
            double baseRent = getDoubleProperty(props, "contract." + i + ".baseRent", 0);
            double commissionRate = getDoubleProperty(props, "contract." + i + ".commissionRate", 0.08);
            ContractStatus status = ContractStatus.valueOf(props.getProperty("contract." + i + ".status", ContractStatus.DRAFT.name()));

            Shop shop = findShopById(ctx.allShops, shopId);
            if (shop != null) {
                LeaseContract contract = new LeaseContract(contractId, shop, ctx.tenant, baseRent, commissionRate);
                contract.restoreStatus(status);
                ctx.contracts.add(contract);
            }
        }

        ctx.payments.clear();
        ctx.paymentToContractId.clear();
        int paymentCount = getIntProperty(props, "payment.count", 0);
        for (int i = 0; i < paymentCount; i++) {
            int paymentId = getIntProperty(props, "payment." + i + ".id", i + 1);
            double amount = getDoubleProperty(props, "payment." + i + ".amount", 0);
            PaymentStatus status = PaymentStatus.valueOf(props.getProperty("payment." + i + ".status", PaymentStatus.UNVERIFIED.name()));
            int contractId = getIntProperty(props, "payment." + i + ".contractId", -1);

            Payment payment = new Payment(paymentId, amount, status);
            ctx.payments.add(payment);
            if (contractId > 0) {
                ctx.paymentToContractId.put(paymentId, contractId);
            }
        }

        int activeContractId = getIntProperty(props, "active.contractId", -1);
        int activePaymentId = getIntProperty(props, "active.paymentId", -1);
        ctx.activeContractId = activeContractId > 0 ? activeContractId : null;
        ctx.activePaymentId = activePaymentId > 0 ? activePaymentId : null;

        int permissionCount = getIntProperty(props, "permission.count", 0);
        Map<String, String> permissions = new HashMap<>();
        for (int i = 0; i < permissionCount; i++) {
            String userId = props.getProperty("permission." + i + ".userId", "");
            String permission = props.getProperty("permission." + i + ".value", "");
            if (!userId.isEmpty()) {
                permissions.put(userId, permission);
            }
        }
        ctx.systemAdmin.replaceUserPermissions(permissions);

        ctx.service.setNextContractId(getIntProperty(props, "counter.nextContractId", ctx.service.getNextContractId()));
        ctx.service.setNextRecordId(getIntProperty(props, "counter.nextRecordId", ctx.service.getNextRecordId()));
        ctx.service.setNextPaymentId(getIntProperty(props, "counter.nextPaymentId", ctx.service.getNextPaymentId()));
        return true;
    }

    private static void saveState(AppContext ctx) throws IOException {
        Properties props = new Properties();

        props.setProperty("shop.count", String.valueOf(ctx.allShops.size()));
        for (int i = 0; i < ctx.allShops.size(); i++) {
            Shop shop = ctx.allShops.get(i);
            props.setProperty("shop." + i + ".id", String.valueOf(shop.getShopId()));
            props.setProperty("shop." + i + ".num", shop.getShopNum());
            props.setProperty("shop." + i + ".status", shop.getStatus().name());
            props.setProperty("shop." + i + ".area", String.valueOf(shop.getArea()));
        }

        props.setProperty("contract.count", String.valueOf(ctx.contracts.size()));
        for (int i = 0; i < ctx.contracts.size(); i++) {
            LeaseContract contract = ctx.contracts.get(i);
            props.setProperty("contract." + i + ".id", String.valueOf(contract.getContractId()));
            props.setProperty("contract." + i + ".shopId", String.valueOf(contract.getShop().getShopId()));
            props.setProperty("contract." + i + ".baseRent", String.valueOf(contract.getBaseRent()));
            props.setProperty("contract." + i + ".commissionRate", String.valueOf(contract.getCommissionRate()));
            props.setProperty("contract." + i + ".status", contract.getStatus().name());
        }

        props.setProperty("payment.count", String.valueOf(ctx.payments.size()));
        for (int i = 0; i < ctx.payments.size(); i++) {
            Payment payment = ctx.payments.get(i);
            props.setProperty("payment." + i + ".id", String.valueOf(payment.getPaymentId()));
            props.setProperty("payment." + i + ".amount", String.valueOf(payment.getAmount()));
            props.setProperty("payment." + i + ".status", payment.getStatus().name());
            Integer contractId = ctx.paymentToContractId.get(payment.getPaymentId());
            props.setProperty("payment." + i + ".contractId", String.valueOf(contractId == null ? -1 : contractId));
        }

        props.setProperty("active.contractId", String.valueOf(ctx.activeContractId == null ? -1 : ctx.activeContractId));
        props.setProperty("active.paymentId", String.valueOf(ctx.activePaymentId == null ? -1 : ctx.activePaymentId));

        Map<String, String> permissions = ctx.systemAdmin.getUserPermissionsSnapshot();
        props.setProperty("permission.count", String.valueOf(permissions.size()));
        int index = 0;
        for (Map.Entry<String, String> entry : permissions.entrySet()) {
            props.setProperty("permission." + index + ".userId", entry.getKey());
            props.setProperty("permission." + index + ".value", entry.getValue());
            index++;
        }

        props.setProperty("counter.nextContractId", String.valueOf(ctx.service.getNextContractId()));
        props.setProperty("counter.nextRecordId", String.valueOf(ctx.service.getNextRecordId()));
        props.setProperty("counter.nextPaymentId", String.valueOf(ctx.service.getNextPaymentId()));

        if (STATE_FILE.getParent() != null) {
            Files.createDirectories(STATE_FILE.getParent());
        }
        try (OutputStream out = Files.newOutputStream(STATE_FILE)) {
            props.store(out, "Shop Rental V1 App State");
        }
    }

    private static int getIntProperty(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static double getDoubleProperty(Properties props, String key, double defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static Shop findShopById(List<Shop> shops, int shopId) {
        for (Shop shop : shops) {
            if (shop.getShopId() == shopId) {
                return shop;
            }
        }
        return null;
    }
}
