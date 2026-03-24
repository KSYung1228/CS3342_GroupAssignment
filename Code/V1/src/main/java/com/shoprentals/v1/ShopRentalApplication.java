package com.shoprentals.v1;

import com.shoprentals.v1.model.Accounting;
import com.shoprentals.v1.model.ContractManager;
import com.shoprentals.v1.model.ContractStatus;
import com.shoprentals.v1.model.Floor;
import com.shoprentals.v1.model.LeaseContract;
import com.shoprentals.v1.model.Payment;
import com.shoprentals.v1.model.PaymentStatus;
import com.shoprentals.v1.model.Shop;
import com.shoprentals.v1.model.ShopStatus;
import com.shoprentals.v1.model.ShopType;
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
        private final List<Shop> allShops;
        private final LeaseContractFactory leaseFactory;
        private final CommissionStrategy commissionStrategy;

        private final Map<String, User> usersByUsername;
        private final Map<String, String> roleByUsername;
        private final Map<String, String> passwordByUsername;

        private final List<LeaseContract> contracts;
        private final List<Payment> payments;
        private final Map<Integer, Integer> paymentToContractId;

        private Integer activeContractId;
        private Integer activePaymentId;
        private int nextUserNumber;

        private AppContext(ShopRentalService service,
                           SystemAdmin systemAdmin,
                           List<Shop> allShops,
                           LeaseContractFactory leaseFactory,
                           CommissionStrategy commissionStrategy) {
            this.service = service;
            this.systemAdmin = systemAdmin;
            this.allShops = allShops;
            this.leaseFactory = leaseFactory;
            this.commissionStrategy = commissionStrategy;
            this.usersByUsername = new HashMap<>();
            this.roleByUsername = new HashMap<>();
            this.passwordByUsername = new HashMap<>();
            this.contracts = new ArrayList<>();
            this.payments = new ArrayList<>();
            this.paymentToContractId = new HashMap<>();
            this.nextUserNumber = 1000;
        }
    }

    public static void main(String[] args) {
        RentalEventBus eventBus = new RentalEventBus();
        eventBus.subscribe(new AuditLogListener());

        ShopRentalService service = new ShopRentalService(eventBus);
        SystemAdmin systemAdmin = new SystemAdmin("U001", "admin", "admin123");

        List<Shop> allShops = new ArrayList<>();
        allShops.add(new Shop(1, "A-101", ShopStatus.OPEN, 35.0, ShopType.FNB));
        allShops.add(new Shop(2, "A-102", ShopStatus.CLOSED, 40.0, ShopType.POPUP));

        AppContext ctx = new AppContext(
                service,
                systemAdmin,
                allShops,
                new StandardLeaseFactory(),
                new ProgressiveCommissionStrategy()
        );

        registerUser(ctx, systemAdmin, "systemadmin", "admin123");
        registerUser(ctx, systemAdmin.createAccount("accounting", "U100", "acct", "pass"), "accounting", "pass");
        registerUser(ctx, systemAdmin.createAccount("tenant", "U200", "freshMart", "pass"), "tenant", "pass");
        registerUser(ctx, systemAdmin.createAccount("contractmanager", "U300", "cm", "pass"), "contractmanager", "pass");

        loadStateQuietly(ctx);

        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                printLoginScreen();
                System.out.print("Username (register/exit): ");
                String username = scanner.nextLine().trim();

                if ("exit".equalsIgnoreCase(username) || "0".equals(username)) {
                    running = false;
                    break;
                }

                if ("register".equalsIgnoreCase(username)) {
                    registerTenantFromInput(scanner, ctx);
                    continue;
                }

                System.out.print("Password: ");
                String password = scanner.nextLine().trim();

                User user = authenticate(ctx, username, password);
                if (user == null) {
                    System.out.println("Login failed.");
                    continue;
                }

                String role = ctx.roleByUsername.getOrDefault(username, "");
                System.out.println("Login successful: " + username + " (" + role + ")");

                switch (role) {
                    case "tenant" -> runTenantMenu(scanner, ctx, (Tenant) user);
                    case "contractmanager" -> runContractManagerMenu(scanner, ctx);
                    case "accounting" -> runAccountingMenu(scanner, ctx);
                    case "systemadmin" -> runAdminMenu(scanner, ctx, username);
                    case "floor" -> runFloorMenu(scanner, ctx, (Floor) user);
                    default -> System.out.println("Unsupported role.");
                }
            }
        }

        saveStateQuietly(ctx);
        System.out.println("Application ended.");
    }

    private static void printLoginScreen() {
        System.out.println();
        System.out.println("===== Shop Rental System V1 =====");
        System.out.println("Type register to create tenant account.");
    }

    private static void registerTenantFromInput(Scanner scanner, AppContext ctx) {
        System.out.print("New username: ");
        String username = scanner.nextLine().trim();
        if (username.isEmpty() || ctx.usersByUsername.containsKey(username)) {
            System.out.println("Invalid or duplicate username.");
            return;
        }

        System.out.print("Password: ");
        String password = scanner.nextLine().trim();
        if (password.isEmpty()) {
            System.out.println("Password cannot be empty.");
            return;
        }

        String userId = generateUserId(ctx);
        User user = ctx.systemAdmin.createAccount("tenant", userId, username, password);
        registerUser(ctx, user, "tenant", password);
        saveStateQuietly(ctx);
        System.out.println("Register success. You can now login as: " + username);
    }

    private static User authenticate(AppContext ctx, String username, String password) {
        User user = ctx.usersByUsername.get(username);
        String expectedPassword = ctx.passwordByUsername.get(username);
        if (user == null || expectedPassword == null) {
            return null;
        }
        if (!expectedPassword.equals(password)) {
            return null;
        }
        return user.login(password) ? user : null;
    }

    private static void runTenantMenu(Scanner scanner, AppContext ctx, Tenant tenant) {
        boolean back = false;
        while (!back) {
            System.out.println();
            System.out.println("--- Tenant Menu ---");
            System.out.println("1. View open shops");
            System.out.println("2. Request lease contract");
            System.out.println("3. Select active contract");
            System.out.println("4. Upload monthly sales record");
            System.out.println("5. View my contracts/payments");
            System.out.println("0. Logout");
            System.out.print("Choose an option: ");
            String option = scanner.nextLine().trim();

            try {
                switch (option) {
                    case "1" -> printOpenShops(tenant, ctx.allShops, ctx.service);
                    case "2" -> requestLease(scanner, ctx, tenant);
                    case "3" -> selectActiveContractForTenant(scanner, ctx, tenant);
                    case "4" -> uploadMonthlyRecord(scanner, ctx, tenant);
                    case "5" -> printTenantState(ctx, tenant);
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
            System.out.println("5. Create shop");
            System.out.println("6. Edit shop info");
            System.out.println("7. Delete shop");
            System.out.println("8. View all state");
            System.out.println("0. Logout");
            System.out.print("Choose an option: ");
            String option = scanner.nextLine().trim();

            try {
                switch (option) {
                    case "1" -> printContracts(ctx);
                    case "2" -> selectActiveContract(scanner, ctx);
                    case "3" -> approveActiveContract(ctx);
                    case "4" -> updateShopStatus(scanner, ctx);
                    case "5" -> createShop(scanner, ctx);
                    case "6" -> editShop(scanner, ctx);
                    case "7" -> deleteShop(scanner, ctx);
                    case "8" -> printAllState(ctx);
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
            System.out.println("5. View all state");
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

    private static void runFloorMenu(Scanner scanner, AppContext ctx, Floor floor) {
        boolean back = false;
        while (!back) {
            System.out.println();
            System.out.println("--- Floor Menu ---");
            System.out.println("1. Manage store positions");
            System.out.println("2. List shops");
            System.out.println("0. Logout");
            System.out.print("Choose an option: ");
            String option = scanner.nextLine().trim();

            switch (option) {
                case "1" -> floor.manageStorePositions();
                case "2" -> printShops(ctx.allShops);
                case "0" -> back = true;
                default -> System.out.println("Unknown option. Try again.");
            }
        }
    }

    private static void runAdminMenu(Scanner scanner, AppContext ctx, String currentAdminUsername) {
        boolean back = false;
        while (!back) {
            System.out.println();
            System.out.println("--- SystemAdmin Menu ---");
            System.out.println("1. Create user");
            System.out.println("2. Update user password");
            System.out.println("3. Delete user");
            System.out.println("4. List users");
            System.out.println("5. Change user permission");
            System.out.println("6. View user permission");
            System.out.println("7. Create shop");
            System.out.println("8. Edit shop info");
            System.out.println("9. Delete shop");
            System.out.println("10. View all state");
            System.out.println("0. Logout");
            System.out.print("Choose an option: ");
            String option = scanner.nextLine().trim();

            try {
                switch (option) {
                    case "1" -> createUserByAdmin(scanner, ctx);
                    case "2" -> updateUserPasswordByAdmin(scanner, ctx);
                    case "3" -> deleteUserByAdmin(scanner, ctx, currentAdminUsername);
                    case "4" -> listUsers(ctx);
                    case "5" -> {
                        System.out.print("Enter target userId: ");
                        String userId = scanner.nextLine().trim();
                        System.out.print("Enter permission text: ");
                        String permission = scanner.nextLine().trim();
                        ctx.systemAdmin.changeUserPermission(userId, permission);
                        saveStateQuietly(ctx);
                        System.out.println("Permission updated and saved.");
                    }
                    case "6" -> {
                        System.out.print("Enter userId: ");
                        String userId = scanner.nextLine().trim();
                        System.out.println("Permission: " + ctx.systemAdmin.getPermission(userId));
                    }
                    case "7" -> createShop(scanner, ctx);
                    case "8" -> editShop(scanner, ctx);
                    case "9" -> deleteShop(scanner, ctx);
                    case "10" -> printAllState(ctx);
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

    private static void createUserByAdmin(Scanner scanner, AppContext ctx) {
        System.out.print("Role (tenant/accounting/contractmanager/floor): ");
        String role = scanner.nextLine().trim().toLowerCase();
        if (!isCreatableRole(role)) {
            System.out.println("Unsupported role.");
            return;
        }

        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        if (username.isEmpty() || ctx.usersByUsername.containsKey(username)) {
            System.out.println("Invalid or duplicate username.");
            return;
        }

        System.out.print("Password: ");
        String password = scanner.nextLine().trim();
        if (password.isEmpty()) {
            System.out.println("Password cannot be empty.");
            return;
        }

        String userId = generateUserId(ctx);
        User user = ctx.systemAdmin.createAccount(role, userId, username, password);
        registerUser(ctx, user, role, password);
        saveStateQuietly(ctx);
        System.out.println("User created. userId=" + userId);
    }

    private static void updateUserPasswordByAdmin(Scanner scanner, AppContext ctx) {
        System.out.print("Username to update: ");
        String username = scanner.nextLine().trim();
        User user = ctx.usersByUsername.get(username);
        if (user == null) {
            System.out.println("User not found.");
            return;
        }

        System.out.print("New password: ");
        String newPassword = scanner.nextLine().trim();
        if (newPassword.isEmpty()) {
            System.out.println("Password cannot be empty.");
            return;
        }

        user.changePassword(newPassword);
        ctx.passwordByUsername.put(username, newPassword);
        saveStateQuietly(ctx);
        System.out.println("Password updated.");
    }

    private static void deleteUserByAdmin(Scanner scanner, AppContext ctx, String currentAdminUsername) {
        System.out.print("Username to delete: ");
        String username = scanner.nextLine().trim();
        User user = ctx.usersByUsername.get(username);
        if (user == null) {
            System.out.println("User not found.");
            return;
        }
        if ("systemadmin".equals(ctx.roleByUsername.get(username))) {
            System.out.println("Cannot delete admin account.");
            return;
        }
        if (username.equals(currentAdminUsername)) {
            System.out.println("Cannot delete current login account.");
            return;
        }

        if (user instanceof Tenant tenant) {
            for (LeaseContract contract : ctx.contracts) {
                if (contract.getTenant() == tenant) {
                    System.out.println("Cannot delete tenant with existing contracts.");
                    return;
                }
            }
        }

        ctx.usersByUsername.remove(username);
        ctx.roleByUsername.remove(username);
        ctx.passwordByUsername.remove(username);
        saveStateQuietly(ctx);
        System.out.println("User deleted.");
    }

    private static void listUsers(AppContext ctx) {
        System.out.println("Users:");
        for (Map.Entry<String, User> entry : ctx.usersByUsername.entrySet()) {
            String username = entry.getKey();
            User user = entry.getValue();
            String role = ctx.roleByUsername.getOrDefault(username, "unknown");
            System.out.println("- username=" + username + ", role=" + role + ", userId=" + user.getUserId());
        }
    }

    private static void requestLease(Scanner scanner, AppContext ctx, Tenant tenant) {
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
        LeaseContract contract = tenant.requestRentStore(selectedShop, ctx.service, ctx.leaseFactory, baseRent);
        ctx.contracts.add(contract);
        ctx.activeContractId = contract.getContractId();
        saveStateQuietly(ctx);
        System.out.println("Contract request created. Contract ID: " + contract.getContractId());
    }

    private static void selectActiveContractForTenant(Scanner scanner, AppContext ctx, Tenant tenant) {
        List<LeaseContract> mine = getContractsForTenant(ctx, tenant);
        if (mine.isEmpty()) {
            System.out.println("No contracts for current tenant.");
            return;
        }
        System.out.println("My contracts:");
        for (LeaseContract contract : mine) {
            System.out.println("- ID=" + contract.getContractId() + ", status=" + contract.getStatus());
        }
        System.out.print("Enter contract id to activate: ");
        int contractId = Integer.parseInt(scanner.nextLine().trim());
        LeaseContract contract = findContractById(ctx, contractId);
        if (contract == null || contract.getTenant() != tenant) {
            System.out.println("Contract not found for current tenant.");
            return;
        }
        ctx.activeContractId = contractId;
        saveStateQuietly(ctx);
        System.out.println("Active contract set to ID: " + contractId);
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

        ContractManager manager = getFirstUserByRole(ctx, "contractmanager", ContractManager.class);
        Tenant tenant = contract.getTenant();
        if (manager == null || tenant == null) {
            System.out.println("Required manager/tenant account missing.");
            return;
        }

        if (!manager.permitRequest(contract)) {
            System.out.println("Active contract is not waiting for approval.");
            return;
        }

        manager.createContract(contract, ctx.service);
        tenant.signContract(contract);
        saveStateQuietly(ctx);
        System.out.println("Contract approved and signed. Status: " + contract.getStatus());
    }

    private static void uploadMonthlyRecord(Scanner scanner, AppContext ctx, Tenant tenant) {
        LeaseContract contract = getActiveContract(ctx);
        if (contract == null) {
            System.out.println("No active contract. Select one first.");
            return;
        }
        if (contract.getTenant() != tenant) {
            System.out.println("Active contract does not belong to current tenant.");
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

        Payment payment = tenant.uploadMonthlyRecord(contract, month, sales, ctx.commissionStrategy, ctx.service);
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

        Accounting accounting = getFirstUserByRole(ctx, "accounting", Accounting.class);
        if (accounting == null) {
            System.out.println("No accounting user found.");
            return;
        }

        boolean confirmed = accounting.confirmPaymentTransfer(payment);
        saveStateQuietly(ctx);
        System.out.println("Payment " + payment.getPaymentId() + " confirmed: " + confirmed);
    }

    private static void createShop(Scanner scanner, AppContext ctx) {
        int nextShopId = 1;
        for (Shop shop : ctx.allShops) {
            nextShopId = Math.max(nextShopId, shop.getShopId() + 1);
        }

        System.out.print("Shop number: ");
        String shopNum = scanner.nextLine().trim();
        if (shopNum.isEmpty()) {
            System.out.println("Shop number cannot be empty.");
            return;
        }

        System.out.print("Area: ");
        double area = Double.parseDouble(scanner.nextLine().trim());

        System.out.print("Type (F&B/POPUP/SOLID): ");
        ShopType type = parseShopTypeInput(scanner.nextLine());
        if (type == null) {
            System.out.println("Invalid shop type.");
            return;
        }

        Shop shop = new Shop(nextShopId, shopNum, ShopStatus.OPEN, area, type);
        ctx.allShops.add(shop);
        saveStateQuietly(ctx);
        System.out.println("Shop created. id=" + shop.getShopId());
    }

    private static void editShop(Scanner scanner, AppContext ctx) {
        Shop shop = chooseShop(scanner, ctx.allShops);
        if (shop == null) {
            System.out.println("Invalid shop id.");
            return;
        }

        System.out.print("New shop number: ");
        String newShopNum = scanner.nextLine().trim();
        System.out.print("New area: ");
        double newArea = Double.parseDouble(scanner.nextLine().trim());

        System.out.print("New type (F&B/POPUP/SOLID): ");
        ShopType newType = parseShopTypeInput(scanner.nextLine());
        if (newType == null) {
            System.out.println("Invalid shop type.");
            return;
        }

        shop.editStoreInfo(newShopNum, newArea, newType);
        saveStateQuietly(ctx);
        System.out.println("Shop updated.");
    }

    private static void deleteShop(Scanner scanner, AppContext ctx) {
        Shop shop = chooseShop(scanner, ctx.allShops);
        if (shop == null) {
            System.out.println("Invalid shop id.");
            return;
        }

        for (LeaseContract contract : ctx.contracts) {
            if (contract.getShop().getShopId() == shop.getShopId()) {
                System.out.println("Cannot delete shop with existing contracts.");
                return;
            }
        }

        ctx.allShops.remove(shop);
        saveStateQuietly(ctx);
        System.out.println("Shop deleted.");
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

        selectedShop.updateStoreStatus(newStatus);

        Accounting accounting = getFirstUserByRole(ctx, "accounting", Accounting.class);
        if (accounting != null) {
            for (LeaseContract contract : ctx.contracts) {
                if (contract.getShop().getShopId() == selectedShop.getShopId()) {
                    accounting.alterRentByStoreStatus(contract, selectedShop);
                }
            }
            accounting.inspectStoreStatus(selectedShop);
        }

        saveStateQuietly(ctx);
        System.out.println("Shop status updated.");
    }

    private static void inspectAllShops(AppContext ctx) {
        Accounting accounting = getFirstUserByRole(ctx, "accounting", Accounting.class);
        if (accounting == null) {
            System.out.println("No accounting user found.");
            return;
        }
        for (Shop shop : ctx.allShops) {
            accounting.inspectStoreStatus(shop);
        }
    }

    private static void printOpenShops(Tenant tenant, List<Shop> allShops, ShopRentalService service) {
        List<Shop> openShops = tenant.searchOpenStore(allShops, service);
        if (openShops.isEmpty()) {
            System.out.println("No open shops available.");
            return;
        }

        System.out.println("Open shops:");
        for (Shop shop : openShops) {
            System.out.println("- " + shop.getShopId() + " | " + shop.getShopNum() + " | type=" + shop.getType() + " | area=" + shop.getArea());
        }
    }

    private static void printContracts(AppContext ctx) {
        if (ctx.contracts.isEmpty()) {
            System.out.println("Contracts: none");
            return;
        }

        System.out.println("Contracts:");
        for (LeaseContract contract : ctx.contracts) {
            String tenantUsername = findUsernameByUser(ctx, contract.getTenant());
            String activeTag = (ctx.activeContractId != null && ctx.activeContractId == contract.getContractId()) ? " [ACTIVE]" : "";
            System.out.println("- ID=" + contract.getContractId()
                    + ", tenant=" + tenantUsername
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

    private static void printTenantState(AppContext ctx, Tenant tenant) {
        List<LeaseContract> mine = getContractsForTenant(ctx, tenant);
        if (mine.isEmpty()) {
            System.out.println("My contracts: none");
        } else {
            System.out.println("My contracts:");
            for (LeaseContract contract : mine) {
                String activeTag = (ctx.activeContractId != null && ctx.activeContractId == contract.getContractId()) ? " [ACTIVE]" : "";
                System.out.println("- ID=" + contract.getContractId()
                        + ", shop=" + contract.getShop().getShopNum()
                        + ", status=" + contract.getStatus()
                        + activeTag);
            }
        }

        System.out.println("My payments:");
        boolean hasMyPayment = false;
        for (Payment payment : ctx.payments) {
            Integer contractId = ctx.paymentToContractId.get(payment.getPaymentId());
            LeaseContract linked = contractId == null ? null : findContractById(ctx, contractId);
            if (linked != null && linked.getTenant() == tenant) {
                hasMyPayment = true;
                String activeTag = (ctx.activePaymentId != null && ctx.activePaymentId == payment.getPaymentId()) ? " [ACTIVE]" : "";
                System.out.println("- ID=" + payment.getPaymentId() + ", amount=" + payment.getAmount() + ", status=" + payment.getStatus() + activeTag);
            }
        }
        if (!hasMyPayment) {
            System.out.println("- none");
        }
    }

    private static void printAllState(AppContext ctx) {
        printShops(ctx.allShops);
        printContracts(ctx);
        printPayments(ctx);
    }

    private static void printShops(List<Shop> shops) {
        if (shops.isEmpty()) {
            System.out.println("Shops: none");
            return;
        }
        System.out.println("Shops:");
        for (Shop shop : shops) {
            System.out.println("- id=" + shop.getShopId() + ", num=" + shop.getShopNum() + ", type=" + shop.getType() + ", status=" + shop.getStatus() + ", area=" + shop.getArea());
        }
    }

    private static Shop chooseShop(Scanner scanner, List<Shop> shops) {
        if (shops.isEmpty()) {
            return null;
        }
        printShops(shops);
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

    private static Payment findPaymentById(AppContext ctx, int paymentId) {
        for (Payment payment : ctx.payments) {
            if (payment.getPaymentId() == paymentId) {
                return payment;
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

    private static Payment getActivePayment(AppContext ctx) {
        if (ctx.activePaymentId == null) {
            return null;
        }
        return findPaymentById(ctx, ctx.activePaymentId);
    }

    private static List<LeaseContract> getContractsForTenant(AppContext ctx, Tenant tenant) {
        List<LeaseContract> result = new ArrayList<>();
        for (LeaseContract contract : ctx.contracts) {
            if (contract.getTenant() == tenant) {
                result.add(contract);
            }
        }
        return result;
    }

    private static boolean isCreatableRole(String role) {
        return "tenant".equals(role) || "accounting".equals(role) || "contractmanager".equals(role) || "floor".equals(role);
    }

    private static String generateUserId(AppContext ctx) {
        String userId = "U" + ctx.nextUserNumber;
        ctx.nextUserNumber++;
        return userId;
    }

    private static void registerUser(AppContext ctx, User user, String role, String password) {
        String username = user.getUsername();
        ctx.usersByUsername.put(username, user);
        ctx.roleByUsername.put(username, role);
        ctx.passwordByUsername.put(username, password);
    }

    private static String findUsernameByUser(AppContext ctx, User user) {
        for (Map.Entry<String, User> entry : ctx.usersByUsername.entrySet()) {
            if (entry.getValue() == user) {
                return entry.getKey();
            }
        }
        return "unknown";
    }

    private static <T extends User> T getFirstUserByRole(AppContext ctx, String role, Class<T> clazz) {
        for (Map.Entry<String, String> entry : ctx.roleByUsername.entrySet()) {
            if (role.equals(entry.getValue())) {
                User user = ctx.usersByUsername.get(entry.getKey());
                if (clazz.isInstance(user)) {
                    return clazz.cast(user);
                }
            }
        }
        return null;
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
                ShopType type = getShopTypeProperty(props, "shop." + i + ".type", ShopType.SOLID);
                ctx.allShops.add(new Shop(shopId, shopNum, status, area, type));
            }
        }

        int userCount = getIntProperty(props, "user.count", 0);
        if (userCount > 0) {
            Map<String, User> loadedUsers = new HashMap<>();
            Map<String, String> loadedRoles = new HashMap<>();
            Map<String, String> loadedPasswords = new HashMap<>();

            for (int i = 0; i < userCount; i++) {
                String username = props.getProperty("user." + i + ".username", "");
                String userId = props.getProperty("user." + i + ".userId", "");
                String role = props.getProperty("user." + i + ".role", "");
                String password = props.getProperty("user." + i + ".password", "");
                if (username.isEmpty() || userId.isEmpty() || role.isEmpty()) {
                    continue;
                }

                User user = createUserByRole(ctx, role, userId, username, password);
                if (user != null) {
                    loadedUsers.put(username, user);
                    loadedRoles.put(username, role);
                    loadedPasswords.put(username, password);
                }
            }

            if (!loadedUsers.isEmpty()) {
                ctx.usersByUsername.clear();
                ctx.usersByUsername.putAll(loadedUsers);
                ctx.roleByUsername.clear();
                ctx.roleByUsername.putAll(loadedRoles);
                ctx.passwordByUsername.clear();
                ctx.passwordByUsername.putAll(loadedPasswords);
            }
        }

        ctx.nextUserNumber = getIntProperty(props, "user.nextNumber", ctx.nextUserNumber);

        ctx.contracts.clear();
        int contractCount = getIntProperty(props, "contract.count", 0);
        for (int i = 0; i < contractCount; i++) {
            int contractId = getIntProperty(props, "contract." + i + ".id", i + 1);
            int shopId = getIntProperty(props, "contract." + i + ".shopId", -1);
            String tenantUsername = props.getProperty("contract." + i + ".tenant", "");
            double baseRent = getDoubleProperty(props, "contract." + i + ".baseRent", 0);
            double commissionRate = getDoubleProperty(props, "contract." + i + ".commissionRate", 0.08);
            ContractStatus status = ContractStatus.valueOf(props.getProperty("contract." + i + ".status", ContractStatus.DRAFT.name()));

            Shop shop = findShopById(ctx.allShops, shopId);
            User tenantUser = ctx.usersByUsername.get(tenantUsername);
            if (shop != null && tenantUser instanceof Tenant tenant) {
                LeaseContract contract = new LeaseContract(contractId, shop, tenant, baseRent, commissionRate);
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
            props.setProperty("shop." + i + ".type", shop.getType().name());
        }

        props.setProperty("user.count", String.valueOf(ctx.usersByUsername.size()));
        int userIndex = 0;
        for (Map.Entry<String, User> entry : ctx.usersByUsername.entrySet()) {
            String username = entry.getKey();
            User user = entry.getValue();
            String role = ctx.roleByUsername.getOrDefault(username, "");
            String password = ctx.passwordByUsername.getOrDefault(username, "");

            props.setProperty("user." + userIndex + ".username", username);
            props.setProperty("user." + userIndex + ".userId", user.getUserId());
            props.setProperty("user." + userIndex + ".role", role);
            props.setProperty("user." + userIndex + ".password", password);
            userIndex++;
        }
        props.setProperty("user.nextNumber", String.valueOf(ctx.nextUserNumber));

        props.setProperty("contract.count", String.valueOf(ctx.contracts.size()));
        for (int i = 0; i < ctx.contracts.size(); i++) {
            LeaseContract contract = ctx.contracts.get(i);
            props.setProperty("contract." + i + ".id", String.valueOf(contract.getContractId()));
            props.setProperty("contract." + i + ".shopId", String.valueOf(contract.getShop().getShopId()));
            props.setProperty("contract." + i + ".tenant", findUsernameByUser(ctx, contract.getTenant()));
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
        int permissionIndex = 0;
        for (Map.Entry<String, String> entry : permissions.entrySet()) {
            props.setProperty("permission." + permissionIndex + ".userId", entry.getKey());
            props.setProperty("permission." + permissionIndex + ".value", entry.getValue());
            permissionIndex++;
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

    private static User createUserByRole(AppContext ctx, String role, String userId, String username, String password) {
        return switch (role) {
            case "systemadmin" -> {
                if ("admin".equals(username)) {
                    ctx.systemAdmin.changePassword(password);
                    yield ctx.systemAdmin;
                }
                yield new SystemAdmin(userId, username, password);
            }
            case "tenant", "accounting", "contractmanager", "floor" -> ctx.systemAdmin.createAccount(role, userId, username, password);
            default -> null;
        };
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

    private static ShopType getShopTypeProperty(Properties props, String key, ShopType defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        ShopType parsed = parseShopTypeInput(value);
        return parsed == null ? defaultValue : parsed;
    }

    private static ShopType parseShopTypeInput(String input) {
        if (input == null) {
            return null;
        }

        String normalized = input.trim().toUpperCase();
        return switch (normalized) {
            case "F&B", "FNB" -> ShopType.FNB;
            case "POPUP", "POP-UP", "POP_UP" -> ShopType.POPUP;
            case "SOLID", "SOILD" -> ShopType.SOLID;
            default -> null;
        };
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
