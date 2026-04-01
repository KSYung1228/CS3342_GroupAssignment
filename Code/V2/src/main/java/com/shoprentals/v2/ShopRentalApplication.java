package com.shoprentals.v2;

import com.shoprentals.v2.model.*;
import com.shoprentals.v2.pattern.factory.*;
import com.shoprentals.v2.pattern.observer.*;
import com.shoprentals.v2.pattern.strategy.*;
import com.shoprentals.v2.service.ShopRentalService;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * V2 CLI entry point. Supports both interactive and scripted stdin.
 */
public class ShopRentalApplication {
    private static final Path STATE_FILE = Paths.get("data", "app-state.properties");

    static class AppContext {
        final ShopRentalService service;
        final SystemAdmin systemAdmin;
        final List<Shop> allShops;
        final LeaseContractFactory leaseFactory;
        final CommissionStrategy commissionStrategy;
        final Map<String, User> usersByUsername = new HashMap<>();
        final Map<String, String> roleByUsername = new HashMap<>();
        final Map<String, String> passwordByUsername = new HashMap<>();
        final List<LeaseContract> contracts = new ArrayList<>();
        final List<Payment> payments = new ArrayList<>();
        final Map<Integer, Integer> paymentToContractId = new HashMap<>();
        final Map<Integer, String> paymentMonthById = new HashMap<>();
        final Map<Integer, Double> paymentSalesById = new HashMap<>();
        final Map<String, Integer> activeContractIdByUser = new HashMap<>();
        Integer activePaymentId = null;
        int nextUserNumber = 1000;

        AppContext(ShopRentalService service, SystemAdmin admin, List<Shop> shops,
                   LeaseContractFactory factory, CommissionStrategy strategy) {
            this.service = service;
            this.systemAdmin = admin;
            this.allShops = shops;
            this.leaseFactory = factory;
            this.commissionStrategy = strategy;
        }
    }

    public static void main(String[] args) {
        RentalEventBus eventBus = new RentalEventBus();
        eventBus.subscribe(new AuditLogListener());
        ShopRentalService service = new ShopRentalService(eventBus);
        SystemAdmin admin = new SystemAdmin("U001", "admin", "admin123");

        List<Shop> shops = new ArrayList<>();
        shops.add(new Shop(1, "A-101", ShopStatus.OPEN,  35.0, ShopType.FNB,   1, 1, 2, 2));
        shops.add(new Shop(2, "A-102", ShopStatus.CLOSED, 40.0, ShopType.POPUP, 3, 1, 2, 2));
        shops.add(new Shop(3, "B-101", ShopStatus.OPEN,  50.0, ShopType.SOLID,  1, 3, 3, 2));
        shops.add(new Shop(4, "B-102", ShopStatus.OPEN,  30.0, ShopType.FNB,    4, 3, 2, 2));

        AppContext ctx = new AppContext(service, admin, shops, new StandardLeaseFactory(), new ProgressiveCommissionStrategy());
        registerUser(ctx, admin, "systemadmin", "admin123");
        registerUser(ctx, admin.createAccount("accounting", "U100", "acct", "pass"), "accounting", "pass");
        registerUser(ctx, admin.createAccount("tenant", "U200", "freshMart", "pass"), "tenant", "pass");
        registerUser(ctx, admin.createAccount("contractmanager", "U300", "cm", "pass"), "contractmanager", "pass");

        loadStateQuietly(ctx);

        try (Scanner sc = new Scanner(System.in)) {
            boolean running = true;
            while (running) {
                System.out.println("\n===== Shop Rental System V2 =====");
                System.out.println("Type 'register' to create tenant account, 'exit' to quit.");
                System.out.print("Username: ");
                String username = sc.nextLine().trim();
                if ("exit".equalsIgnoreCase(username) || "0".equals(username)) { running = false; break; }
                if ("register".equalsIgnoreCase(username)) { registerTenantCLI(sc, ctx); continue; }
                System.out.print("Password: ");
                String password = sc.nextLine().trim();
                User user = authenticate(ctx, username, password);
                if (user == null) { System.out.println("Login failed."); continue; }
                String role = ctx.roleByUsername.getOrDefault(username, "");
                System.out.println("Login successful: " + username + " (" + role + ")");
                switch (role) {
                    case "tenant"          -> runTenantMenu(sc, ctx, (Tenant) user);
                    case "contractmanager" -> runManagerMenu(sc, ctx);
                    case "accounting"      -> runAccountingMenu(sc, ctx);
                    case "systemadmin"     -> runAdminMenu(sc, ctx, username);
                    default -> System.out.println("Unsupported role.");
                }
            }
        }
        saveStateQuietly(ctx);
        System.out.println("Application ended.");
    }

    // ── Auth helpers ──────────────────────────────────────────────────────────

    static void registerUser(AppContext ctx, User user, String role, String password) {
        ctx.usersByUsername.put(user.getUsername(), user);
        ctx.roleByUsername.put(user.getUsername(), role);
        ctx.passwordByUsername.put(user.getUsername(), password);
    }

    static User authenticate(AppContext ctx, String username, String password) {
        User user = ctx.usersByUsername.get(username);
        String expected = ctx.passwordByUsername.get(username);
        if (user == null || expected == null || !expected.equals(password)) return null;
        return user.login(password) ? user : null;
    }

    static String generateUserId(AppContext ctx) {
        return "U" + (++ctx.nextUserNumber);
    }

    private static void registerTenantCLI(Scanner sc, AppContext ctx) {
        System.out.print("New username: ");
        String u = sc.nextLine().trim();
        if (u.isEmpty() || ctx.usersByUsername.containsKey(u)) { System.out.println("Invalid or duplicate username."); return; }
        System.out.print("Password: ");
        String p = sc.nextLine().trim();
        if (p.isEmpty()) { System.out.println("Password cannot be empty."); return; }
        String uid = generateUserId(ctx);
        User user = ctx.systemAdmin.createAccount("tenant", uid, u, p);
        registerUser(ctx, user, "tenant", p);
        saveStateQuietly(ctx);
        System.out.println("Registered. You can now login as: " + u);
    }

    // ── Tenant Menu ───────────────────────────────────────────────────────────

    private static void runTenantMenu(Scanner sc, AppContext ctx, Tenant tenant) {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Tenant Menu ---");
            System.out.println("1. View open shops");
            System.out.println("2. Request lease contract");
            System.out.println("3. Select active contract");
            System.out.println("4. Upload monthly sales record");
            System.out.println("5. View my contracts/payments");
            System.out.println("0. Logout");
            System.out.print("Choose: ");
            String opt = sc.nextLine().trim();
            try {
                switch (opt) {
                    case "1" -> printOpenShops(ctx, tenant);
                    case "2" -> requestLeaseCLI(sc, ctx, tenant);
                    case "3" -> selectContractForTenant(sc, ctx, tenant);
                    case "4" -> uploadSalesCLI(sc, ctx, tenant);
                    case "5" -> printTenantState(ctx, tenant);
                    case "0" -> back = true;
                    default  -> System.out.println("Unknown option.");
                }
            } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        }
    }

    private static void printOpenShops(AppContext ctx, Tenant tenant) {
        List<Shop> open = tenant.searchOpenStore(ctx.allShops, ctx.service);
        if (open.isEmpty()) { System.out.println("No open shops."); return; }
        System.out.println("Open shops:");
        for (Shop s : open)
            System.out.printf("  id=%-3d num=%-8s type=%-6s area=%.1f%n", s.getShopId(), s.getShopNum(), s.getType(), s.getArea());
    }

    private static void requestLeaseCLI(Scanner sc, AppContext ctx, Tenant tenant) {
        printShops(ctx.allShops);
        System.out.print("Enter shop id: ");
        int shopId = Integer.parseInt(sc.nextLine().trim());
        Shop shop = findShopById(ctx, shopId);
        if (shop == null) { System.out.println("Shop not found."); return; }
        if (shop.getStatus() != ShopStatus.OPEN) { System.out.println("Shop is not open."); return; }
        System.out.print("Enter base rent: ");
        double rent = Double.parseDouble(sc.nextLine().trim());
        LeaseContract c = tenant.requestRentStore(shop, ctx.service, ctx.leaseFactory, rent);
        ctx.contracts.add(c);
        ctx.activeContractIdByUser.put(tenant.getUsername(), c.getContractId());
        saveStateQuietly(ctx);
        System.out.println("Contract request created. Contract ID: " + c.getContractId());
    }

    private static void selectContractForTenant(Scanner sc, AppContext ctx, Tenant tenant) {
        List<LeaseContract> mine = getContractsForTenant(ctx, tenant);
        if (mine.isEmpty()) { System.out.println("No contracts found."); return; }
        System.out.println("My contracts:");
        for (LeaseContract c : mine)
            System.out.printf("  ID=%-3d shop=%-8s status=%s%n", c.getContractId(), c.getShop().getShopNum(), c.getStatus());
        System.out.print("Enter contract id to activate: ");
        int id = Integer.parseInt(sc.nextLine().trim());
        LeaseContract c = findContractById(ctx, id);
        if (c == null || c.getTenant() != tenant) { System.out.println("Contract not found for current tenant."); return; }
        ctx.activeContractIdByUser.put(tenant.getUsername(), id);
        saveStateQuietly(ctx);
        System.out.println("Active contract set to ID: " + id);
    }

    private static void uploadSalesCLI(Scanner sc, AppContext ctx, Tenant tenant) {
        Integer cid = ctx.activeContractIdByUser.get(tenant.getUsername());
        if (cid == null) { System.out.println("No active contract. Select one first."); return; }
        LeaseContract c = findContractById(ctx, cid);
        if (c == null) { System.out.println("Active contract not found."); return; }
        if (c.getTenant() != tenant) { System.out.println("Active contract does not belong to current tenant."); return; }
        if (c.getStatus() != ContractStatus.ACTIVE) { System.out.println("Contract is not active yet. Approve first."); return; }
        System.out.print("Enter month (e.g. 2026-03): ");
        String month = sc.nextLine().trim();
        System.out.print("Enter total sales: ");
        double sales = Double.parseDouble(sc.nextLine().trim());
        Payment p = tenant.uploadMonthlyRecord(c, month, sales, ctx.commissionStrategy, ctx.service);
        ctx.payments.add(p);
        ctx.paymentToContractId.put(p.getPaymentId(), c.getContractId());
        ctx.paymentMonthById.put(p.getPaymentId(), month);
        ctx.paymentSalesById.put(p.getPaymentId(), sales);
        ctx.activePaymentId = p.getPaymentId();
        saveStateQuietly(ctx);
        System.out.printf("Payment ID: %d, amount: %.2f%n", p.getPaymentId(), p.getAmount());
    }

    private static void printTenantState(AppContext ctx, Tenant tenant) {
        System.out.println("Contracts:");
        for (LeaseContract c : getContractsForTenant(ctx, tenant))
            System.out.printf("  ID=%-3d shop=%-8s status=%-20s baseRent=%.2f%n",
                    c.getContractId(), c.getShop().getShopNum(), c.getStatus(), c.getBaseRent());
        System.out.println("Payments:");
        for (Payment p : ctx.payments) {
            Integer cid = ctx.paymentToContractId.get(p.getPaymentId());
            if (cid == null) continue;
            LeaseContract c = findContractById(ctx, cid);
            if (c == null || c.getTenant() != tenant) continue;
            System.out.printf("  PayID=%-3d amount=%-10.2f status=%s%n", p.getPaymentId(), p.getAmount(), p.getStatus());
        }
    }

    // ── Contract Manager Menu ─────────────────────────────────────────────────

    private static void runManagerMenu(Scanner sc, AppContext ctx) {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- ContractManager Menu ---");
            System.out.println("1. List contracts");
            System.out.println("2. Select active contract");
            System.out.println("3. Approve active contract");
            System.out.println("4. Terminate active contract");
            System.out.println("5. Update shop status");
            System.out.println("6. Create shop");
            System.out.println("7. Edit shop info");
            System.out.println("8. Delete shop");
            System.out.println("9. View all state");
            System.out.println("0. Logout");
            System.out.print("Choose: ");
            String opt = sc.nextLine().trim();
            try {
                switch (opt) {
                    case "1" -> printContracts(ctx);
                    case "2" -> selectActiveContractGlobal(sc, ctx);
                    case "3" -> approveActiveContract(ctx);
                    case "4" -> terminateActiveContract(ctx);
                    case "5" -> updateShopStatusCLI(sc, ctx);
                    case "6" -> createShopCLI(sc, ctx);
                    case "7" -> editShopCLI(sc, ctx);
                    case "8" -> deleteShopCLI(sc, ctx);
                    case "9" -> printAllState(ctx);
                    case "0" -> back = true;
                    default  -> System.out.println("Unknown option.");
                }
            } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        }
    }

    private static void printContracts(AppContext ctx) {
        if (ctx.contracts.isEmpty()) { System.out.println("No contracts."); return; }
        System.out.println("Contracts:");
        Integer active = getGlobalActiveContractId(ctx);
        for (LeaseContract c : ctx.contracts) {
            String mark = (active != null && active == c.getContractId()) ? " [ACTIVE]" : "";
            System.out.printf("  ID=%-3d tenant=%-12s shop=%-8s status=%-20s baseRent=%.2f%s%n",
                    c.getContractId(), c.getTenant().getUsername(), c.getShop().getShopNum(),
                    c.getStatus(), c.getBaseRent(), mark);
        }
    }

    private static void selectActiveContractGlobal(Scanner sc, AppContext ctx) {
        printContracts(ctx);
        System.out.print("Enter contract id to activate: ");
        int id = Integer.parseInt(sc.nextLine().trim());
        LeaseContract c = findContractById(ctx, id);
        if (c == null) { System.out.println("Contract not found."); return; }
        ctx.activeContractIdByUser.put("__manager__", id);
        saveStateQuietly(ctx);
        System.out.println("Active contract set to ID: " + id);
    }

    private static void approveActiveContract(AppContext ctx) {
        Integer cid = getGlobalActiveContractId(ctx);
        if (cid == null) { System.out.println("No active contract. Select one first."); return; }
        LeaseContract c = findContractById(ctx, cid);
        if (c == null) { System.out.println("Contract not found."); return; }
        ContractManager mgr = getFirstByRole(ctx, "contractmanager", ContractManager.class);
        if (mgr == null) { System.out.println("No contract manager found."); return; }
        if (!mgr.permitRequest(c)) { System.out.println("Contract is not pending approval."); return; }
        mgr.applyApproveContractService(c, ctx.service);
        c.getTenant().signContract(c);
        saveStateQuietly(ctx);
        System.out.println("Contract approved. Status: " + c.getStatus());
    }

    private static void terminateActiveContract(AppContext ctx) {
        Integer cid = getGlobalActiveContractId(ctx);
        if (cid == null) { System.out.println("No active contract. Select one first."); return; }
        LeaseContract c = findContractById(ctx, cid);
        if (c == null) { System.out.println("Contract not found."); return; }
        c.terminate();
        saveStateQuietly(ctx);
        System.out.println("Contract terminated. Status: " + c.getStatus());
    }

    private static void updateShopStatusCLI(Scanner sc, AppContext ctx) {
        printShops(ctx.allShops);
        System.out.print("Enter shop id: ");
        Shop shop = findShopById(ctx, Integer.parseInt(sc.nextLine().trim()));
        if (shop == null) { System.out.println("Shop not found."); return; }
        System.out.println("1=OPEN  2=CLOSED  3=UNDER_REPAIR");
        System.out.print("Choose status: ");
        ShopStatus ns = switch (sc.nextLine().trim()) {
            case "1" -> ShopStatus.OPEN;
            case "2" -> ShopStatus.CLOSED;
            case "3" -> ShopStatus.UNDER_REPAIR;
            default  -> null;
        };
        if (ns == null) { System.out.println("Invalid status."); return; }
        shop.updateStoreStatus(ns);
        Accounting acct = getFirstByRole(ctx, "accounting", Accounting.class);
        if (acct != null) {
            for (LeaseContract c : ctx.contracts)
                if (c.getShop().getShopId() == shop.getShopId()) acct.alterRentByStoreStatus(c, shop);
        }
        saveStateQuietly(ctx);
        System.out.println("Shop status updated to " + ns);
    }

    private static void createShopCLI(Scanner sc, AppContext ctx) {
        int nextId = ctx.allShops.stream().mapToInt(Shop::getShopId).max().orElse(0) + 1;
        System.out.print("Shop number: ");
        String num = sc.nextLine().trim();
        if (num.isEmpty()) { System.out.println("Shop number cannot be empty."); return; }
        System.out.print("Area: ");
        double area = Double.parseDouble(sc.nextLine().trim());
        System.out.print("Type (FNB/POPUP/SOLID): ");
        ShopType type = parseShopType(sc.nextLine().trim());
        if (type == null) { System.out.println("Invalid type."); return; }
        System.out.print("Grid posX posY width height (e.g. 1 1 2 2): ");
        String[] pos = sc.nextLine().trim().split("\\s+");
        int px = pos.length > 0 ? parseInt(pos[0], 0) : 0;
        int py = pos.length > 1 ? parseInt(pos[1], 0) : 0;
        int pw = pos.length > 2 ? parseInt(pos[2], 2) : 2;
        int ph = pos.length > 3 ? parseInt(pos[3], 2) : 2;
        Shop shop = new Shop(nextId, num, ShopStatus.OPEN, area, type, px, py, pw, ph);
        ctx.allShops.add(shop);
        saveStateQuietly(ctx);
        System.out.println("Shop created. id=" + shop.getShopId());
    }

    private static void editShopCLI(Scanner sc, AppContext ctx) {
        printShops(ctx.allShops);
        System.out.print("Enter shop id: ");
        Shop shop = findShopById(ctx, Integer.parseInt(sc.nextLine().trim()));
        if (shop == null) { System.out.println("Shop not found."); return; }
        System.out.print("New shop number: ");
        String num = sc.nextLine().trim();
        System.out.print("New area: ");
        double area = Double.parseDouble(sc.nextLine().trim());
        System.out.print("New type (FNB/POPUP/SOLID): ");
        ShopType type = parseShopType(sc.nextLine().trim());
        if (type == null) { System.out.println("Invalid type."); return; }
        shop.editStoreInfo(num, area, type);
        saveStateQuietly(ctx);
        System.out.println("Shop updated.");
    }

    private static void deleteShopCLI(Scanner sc, AppContext ctx) {
        printShops(ctx.allShops);
        System.out.print("Enter shop id: ");
        Shop shop = findShopById(ctx, Integer.parseInt(sc.nextLine().trim()));
        if (shop == null) { System.out.println("Shop not found."); return; }
        for (LeaseContract c : ctx.contracts)
            if (c.getShop().getShopId() == shop.getShopId()) { System.out.println("Cannot delete shop with existing contracts."); return; }
        ctx.allShops.remove(shop);
        saveStateQuietly(ctx);
        System.out.println("Shop deleted.");
    }

    // ── Accounting Menu ───────────────────────────────────────────────────────

    private static void runAccountingMenu(Scanner sc, AppContext ctx) {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Accounting Menu ---");
            System.out.println("1. List payments");
            System.out.println("2. Select active payment");
            System.out.println("3. Confirm active payment");
            System.out.println("4. Inspect all shops");
            System.out.println("5. View all state");
            System.out.println("0. Logout");
            System.out.print("Choose: ");
            String opt = sc.nextLine().trim();
            try {
                switch (opt) {
                    case "1" -> printPayments(ctx);
                    case "2" -> selectActivePayment(sc, ctx);
                    case "3" -> confirmActivePayment(ctx);
                    case "4" -> ctx.allShops.forEach(s -> System.out.println("  " + s));
                    case "5" -> printAllState(ctx);
                    case "0" -> back = true;
                    default  -> System.out.println("Unknown option.");
                }
            } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        }
    }

    private static void printPayments(AppContext ctx) {
        if (ctx.payments.isEmpty()) { System.out.println("No payments."); return; }
        System.out.println("Payments:");
        for (Payment p : ctx.payments) {
            String mark = (ctx.activePaymentId != null && ctx.activePaymentId == p.getPaymentId()) ? " [ACTIVE]" : "";
            System.out.printf("  ID=%-3d amount=%-10.2f status=%s%s%n",
                    p.getPaymentId(), p.getAmount(), p.getStatus(), mark);
        }
    }

    private static void selectActivePayment(Scanner sc, AppContext ctx) {
        printPayments(ctx);
        System.out.print("Enter payment id to activate: ");
        int id = Integer.parseInt(sc.nextLine().trim());
        Payment p = findPaymentById(ctx, id);
        if (p == null) { System.out.println("Payment not found."); return; }
        ctx.activePaymentId = id;
        saveStateQuietly(ctx);
        System.out.println("Active payment set to ID: " + id);
    }

    private static void confirmActivePayment(AppContext ctx) {
        if (ctx.activePaymentId == null) { System.out.println("No active payment. Select one first."); return; }
        Payment p = findPaymentById(ctx, ctx.activePaymentId);
        if (p == null) { System.out.println("Payment not found."); return; }
        Accounting acct = getFirstByRole(ctx, "accounting", Accounting.class);
        if (acct == null) { System.out.println("No accounting user found."); return; }
        boolean ok = acct.confirmPaymentTransfer(p);
        saveStateQuietly(ctx);
        System.out.println("Payment " + p.getPaymentId() + " confirmed: " + ok);
    }

    // ── Admin Menu ────────────────────────────────────────────────────────────

    private static void runAdminMenu(Scanner sc, AppContext ctx, String currentAdmin) {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- SystemAdmin Menu ---");
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
            System.out.print("Choose: ");
            String opt = sc.nextLine().trim();
            try {
                switch (opt) {
                    case "1"  -> createUserCLI(sc, ctx);
                    case "2"  -> updatePasswordCLI(sc, ctx);
                    case "3"  -> deleteUserCLI(sc, ctx, currentAdmin);
                    case "4"  -> listUsers(ctx);
                    case "5"  -> { System.out.print("UserId: "); String uid = sc.nextLine().trim();
                                   System.out.print("Permission: "); String perm = sc.nextLine().trim();
                                   ctx.systemAdmin.changeUserPermission(uid, perm); saveStateQuietly(ctx);
                                   System.out.println("Permission updated."); }
                    case "6"  -> { System.out.print("UserId: "); String uid = sc.nextLine().trim();
                                   System.out.println("Permission: " + ctx.systemAdmin.getPermission(uid)); }
                    case "7"  -> createShopCLI(sc, ctx);
                    case "8"  -> editShopCLI(sc, ctx);
                    case "9"  -> deleteShopCLI(sc, ctx);
                    case "10" -> printAllState(ctx);
                    case "0"  -> back = true;
                    default   -> System.out.println("Unknown option.");
                }
            } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
        }
    }

    private static void createUserCLI(Scanner sc, AppContext ctx) {
        System.out.print("Role (tenant/accounting/contractmanager): ");
        String role = sc.nextLine().trim().toLowerCase();
        if (!List.of("tenant","accounting","contractmanager").contains(role)) { System.out.println("Unsupported role."); return; }
        System.out.print("Username: ");
        String u = sc.nextLine().trim();
        if (u.isEmpty() || ctx.usersByUsername.containsKey(u)) { System.out.println("Invalid or duplicate username."); return; }
        System.out.print("Password: ");
        String p = sc.nextLine().trim();
        if (p.isEmpty()) { System.out.println("Password cannot be empty."); return; }
        String uid = generateUserId(ctx);
        User user = ctx.systemAdmin.createAccount(role, uid, u, p);
        registerUser(ctx, user, role, p);
        saveStateQuietly(ctx);
        System.out.println("User created. userId=" + uid);
    }

    private static void updatePasswordCLI(Scanner sc, AppContext ctx) {
        System.out.print("Username: ");
        String u = sc.nextLine().trim();
        User user = ctx.usersByUsername.get(u);
        if (user == null) { System.out.println("User not found."); return; }
        System.out.print("New password: ");
        String p = sc.nextLine().trim();
        if (p.isEmpty()) { System.out.println("Password cannot be empty."); return; }
        user.changePassword(p);
        ctx.passwordByUsername.put(u, p);
        saveStateQuietly(ctx);
        System.out.println("Password updated.");
    }

    private static void deleteUserCLI(Scanner sc, AppContext ctx, String currentAdmin) {
        System.out.print("Username to delete: ");
        String u = sc.nextLine().trim();
        User user = ctx.usersByUsername.get(u);
        if (user == null) { System.out.println("User not found."); return; }
        if ("systemadmin".equals(ctx.roleByUsername.get(u))) { System.out.println("Cannot delete admin."); return; }
        if (u.equals(currentAdmin)) { System.out.println("Cannot delete current login."); return; }
        if (user instanceof Tenant t) {
            for (LeaseContract c : ctx.contracts)
                if (c.getTenant() == t) { System.out.println("Cannot delete tenant with existing contracts."); return; }
        }
        ctx.usersByUsername.remove(u);
        ctx.roleByUsername.remove(u);
        ctx.passwordByUsername.remove(u);
        saveStateQuietly(ctx);
        System.out.println("User deleted.");
    }

    private static void listUsers(AppContext ctx) {
        System.out.println("Users:");
        for (Map.Entry<String, User> e : ctx.usersByUsername.entrySet())
            System.out.printf("  username=%-15s role=%-18s userId=%s%n",
                    e.getKey(), ctx.roleByUsername.get(e.getKey()), e.getValue().getUserId());
    }

    private static void printAllState(AppContext ctx) {
        System.out.println("=== All State ===");
        System.out.println("Shops:"); ctx.allShops.forEach(s -> System.out.println("  " + s));
        System.out.println("Contracts:"); printContracts(ctx);
        System.out.println("Payments:"); printPayments(ctx);
    }

    private static void printShops(List<Shop> shops) {
        System.out.println("Shops:");
        for (Shop s : shops)
            System.out.printf("  id=%-3d num=%-8s type=%-6s status=%-12s area=%.1f%n",
                    s.getShopId(), s.getShopNum(), s.getType(), s.getStatus(), s.getArea());
    }

    // ── Lookup helpers ────────────────────────────────────────────────────────

    static Shop findShopById(AppContext ctx, int id) {
        return ctx.allShops.stream().filter(s -> s.getShopId() == id).findFirst().orElse(null);
    }

    static LeaseContract findContractById(AppContext ctx, int id) {
        return ctx.contracts.stream().filter(c -> c.getContractId() == id).findFirst().orElse(null);
    }

    static Payment findPaymentById(AppContext ctx, int id) {
        return ctx.payments.stream().filter(p -> p.getPaymentId() == id).findFirst().orElse(null);
    }

    static List<LeaseContract> getContractsForTenant(AppContext ctx, Tenant tenant) {
        return ctx.contracts.stream().filter(c -> c.getTenant() == tenant).toList();
    }

    static Integer getGlobalActiveContractId(AppContext ctx) {
        return ctx.activeContractIdByUser.get("__manager__");
    }

    @SuppressWarnings("unchecked")
    static <T> T getFirstByRole(AppContext ctx, String role, Class<T> clazz) {
        for (Map.Entry<String, User> e : ctx.usersByUsername.entrySet())
            if (role.equals(ctx.roleByUsername.get(e.getKey())) && clazz.isInstance(e.getValue()))
                return (T) e.getValue();
        return null;
    }

    static ShopType parseShopType(String s) {
        return switch (s.toUpperCase().replace("&","").replace("F&B","FNB").replace("F B","FNB")) {
            case "FNB","F&B","FB" -> ShopType.FNB;
            case "POPUP"          -> ShopType.POPUP;
            case "SOLID"          -> ShopType.SOLID;
            default               -> null;
        };
    }

    static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    static void saveStateQuietly(AppContext ctx) {
        try { saveState(ctx); } catch (Exception ignored) {}
    }

    static void loadStateQuietly(AppContext ctx) {
        try {
            if (!Files.exists(STATE_FILE)) { System.out.println("No saved state found. Using default data."); return; }
            loadState(ctx);
            System.out.println("State loaded from " + STATE_FILE);
        } catch (Exception e) { System.out.println("Failed to load state: " + e.getMessage()); }
    }

    private static void saveState(AppContext ctx) throws IOException {
        Properties p = new Properties();
        // shops
        p.setProperty("shop.count", String.valueOf(ctx.allShops.size()));
        for (int i = 0; i < ctx.allShops.size(); i++) {
            Shop s = ctx.allShops.get(i);
            String pfx = "shop." + i + ".";
            p.setProperty(pfx + "id",     String.valueOf(s.getShopId()));
            p.setProperty(pfx + "num",    s.getShopNum());
            p.setProperty(pfx + "status", s.getStatus().name());
            p.setProperty(pfx + "area",   String.valueOf(s.getArea()));
            p.setProperty(pfx + "type",   s.getType().name());
            p.setProperty(pfx + "posX",   String.valueOf(s.getPosX()));
            p.setProperty(pfx + "posY",   String.valueOf(s.getPosY()));
            p.setProperty(pfx + "width",  String.valueOf(s.getWidth()));
            p.setProperty(pfx + "height", String.valueOf(s.getHeight()));
        }
        // users
        p.setProperty("user.count", String.valueOf(ctx.usersByUsername.size()));
        int ui = 0;
        for (Map.Entry<String, User> e : ctx.usersByUsername.entrySet()) {
            String pfx = "user." + ui + ".";
            p.setProperty(pfx + "username", e.getKey());
            p.setProperty(pfx + "userId",   e.getValue().getUserId());
            p.setProperty(pfx + "role",     ctx.roleByUsername.getOrDefault(e.getKey(), ""));
            p.setProperty(pfx + "password", ctx.passwordByUsername.getOrDefault(e.getKey(), ""));
            ui++;
        }
        p.setProperty("nextUserNumber", String.valueOf(ctx.nextUserNumber));
        // contracts
        p.setProperty("contract.count", String.valueOf(ctx.contracts.size()));
        for (int i = 0; i < ctx.contracts.size(); i++) {
            LeaseContract c = ctx.contracts.get(i);
            String pfx = "contract." + i + ".";
            p.setProperty(pfx + "id",             String.valueOf(c.getContractId()));
            p.setProperty(pfx + "shopId",         String.valueOf(c.getShop().getShopId()));
            p.setProperty(pfx + "tenant",         c.getTenant().getUsername());
            p.setProperty(pfx + "baseRent",       String.valueOf(c.getBaseRent()));
            p.setProperty(pfx + "commissionRate", String.valueOf(c.getCommissionRate()));
            p.setProperty(pfx + "status",         c.getStatus().name());
        }
        // payments
        p.setProperty("payment.count", String.valueOf(ctx.payments.size()));
        for (int i = 0; i < ctx.payments.size(); i++) {
            Payment pay = ctx.payments.get(i);
            String pfx = "payment." + i + ".";
            p.setProperty(pfx + "id",         String.valueOf(pay.getPaymentId()));
            p.setProperty(pfx + "amount",     String.valueOf(pay.getAmount()));
            p.setProperty(pfx + "status",     pay.getStatus().name());
            p.setProperty(pfx + "contractId", String.valueOf(ctx.paymentToContractId.getOrDefault(pay.getPaymentId(), 0)));
            p.setProperty(pfx + "month",      ctx.paymentMonthById.getOrDefault(pay.getPaymentId(), ""));
            p.setProperty(pfx + "sales",      String.valueOf(ctx.paymentSalesById.getOrDefault(pay.getPaymentId(), 0.0)));
        }
        // active selections
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : ctx.activeContractIdByUser.entrySet())
            sb.append(e.getKey()).append("=").append(e.getValue()).append(";");
        p.setProperty("activeContracts", sb.toString());
        if (ctx.activePaymentId != null) p.setProperty("activePaymentId", String.valueOf(ctx.activePaymentId));
        // counters
        p.setProperty("nextContractId", String.valueOf(ctx.service.getNextContractId()));
        p.setProperty("nextRecordId",   String.valueOf(ctx.service.getNextRecordId()));
        p.setProperty("nextPaymentId",  String.valueOf(ctx.service.getNextPaymentId()));
        // permissions
        Map<String, String> perms = ctx.systemAdmin.getUserPermissionsSnapshot();
        p.setProperty("perm.count", String.valueOf(perms.size()));
        int pi = 0;
        for (Map.Entry<String, String> e : perms.entrySet()) {
            p.setProperty("perm." + pi + ".userId",     e.getKey());
            p.setProperty("perm." + pi + ".permission", e.getValue());
            pi++;
        }
        Files.createDirectories(STATE_FILE.getParent());
        try (OutputStream os = Files.newOutputStream(STATE_FILE)) {
            p.store(os, "V2 App State");
        }
    }

    private static void loadState(AppContext ctx) throws IOException {
        Properties p = new Properties();
        try (InputStream is = Files.newInputStream(STATE_FILE)) { p.load(is); }

        // shops
        int sc2 = parseInt(p.getProperty("shop.count", "0"), 0);
        ctx.allShops.clear();
        for (int i = 0; i < sc2; i++) {
            String pfx = "shop." + i + ".";
            int id = parseInt(p.getProperty(pfx + "id", "0"), 0);
            String num = p.getProperty(pfx + "num", "");
            ShopStatus st = ShopStatus.valueOf(p.getProperty(pfx + "status", "OPEN"));
            double area = Double.parseDouble(p.getProperty(pfx + "area", "0"));
            ShopType type = ShopType.valueOf(p.getProperty(pfx + "type", "SOLID"));
            int px = parseInt(p.getProperty(pfx + "posX", "0"), 0);
            int py = parseInt(p.getProperty(pfx + "posY", "0"), 0);
            int pw = parseInt(p.getProperty(pfx + "width", "2"), 2);
            int ph = parseInt(p.getProperty(pfx + "height", "2"), 2);
            ctx.allShops.add(new Shop(id, num, st, area, type, px, py, pw, ph));
        }
        // users
        int uc = parseInt(p.getProperty("user.count", "0"), 0);
        ctx.usersByUsername.clear(); ctx.roleByUsername.clear(); ctx.passwordByUsername.clear();
        for (int i = 0; i < uc; i++) {
            String pfx = "user." + i + ".";
            String username = p.getProperty(pfx + "username", "");
            String userId   = p.getProperty(pfx + "userId", "");
            String role     = p.getProperty(pfx + "role", "");
            String password = p.getProperty(pfx + "password", "");
            User user = switch (role) {
                case "systemadmin"     -> new SystemAdmin(userId, username, password);
                case "accounting"      -> new Accounting(userId, username, password);
                case "contractmanager" -> new ContractManager(userId, username, password);
                case "tenant"          -> new Tenant(userId, username, password, username + " contact");
                default -> null;
            };
            if (user != null) registerUser(ctx, user, role, password);
        }
        ctx.nextUserNumber = parseInt(p.getProperty("nextUserNumber", "1000"), 1000);
        // contracts
        int cc = parseInt(p.getProperty("contract.count", "0"), 0);
        ctx.contracts.clear();
        for (int i = 0; i < cc; i++) {
            String pfx = "contract." + i + ".";
            int id = parseInt(p.getProperty(pfx + "id", "0"), 0);
            int shopId = parseInt(p.getProperty(pfx + "shopId", "0"), 0);
            String tenantName = p.getProperty(pfx + "tenant", "");
            double baseRent = Double.parseDouble(p.getProperty(pfx + "baseRent", "0"));
            double commRate = Double.parseDouble(p.getProperty(pfx + "commissionRate", "0.08"));
            ContractStatus status = ContractStatus.valueOf(p.getProperty(pfx + "status", "DRAFT"));
            Shop shop = findShopById(ctx, shopId);
            User u = ctx.usersByUsername.get(tenantName);
            if (shop != null && u instanceof Tenant tenant) {
                LeaseContract c = new LeaseContract(id, shop, tenant, baseRent, commRate);
                c.restoreStatus(status);
                ctx.contracts.add(c);
            }
        }
        // payments
        int pc = parseInt(p.getProperty("payment.count", "0"), 0);
        ctx.payments.clear();
        for (int i = 0; i < pc; i++) {
            String pfx = "payment." + i + ".";
            int id = parseInt(p.getProperty(pfx + "id", "0"), 0);
            double amount = Double.parseDouble(p.getProperty(pfx + "amount", "0"));
            PaymentStatus ps = PaymentStatus.valueOf(p.getProperty(pfx + "status", "UNVERIFIED"));
            int cid = parseInt(p.getProperty(pfx + "contractId", "0"), 0);
            String month = p.getProperty(pfx + "month", "");
            double sales = Double.parseDouble(p.getProperty(pfx + "sales", "0"));
            ctx.payments.add(new Payment(id, amount, ps));
            ctx.paymentToContractId.put(id, cid);
            ctx.paymentMonthById.put(id, month);
            ctx.paymentSalesById.put(id, sales);
        }
        // active selections
        String ac = p.getProperty("activeContracts", "");
        ctx.activeContractIdByUser.clear();
        if (!ac.isBlank()) {
            for (String part : ac.split(";")) {
                String[] kv = part.split("=");
                if (kv.length == 2) ctx.activeContractIdByUser.put(kv[0], parseInt(kv[1], 0));
            }
        }
        String apid = p.getProperty("activePaymentId", "");
        ctx.activePaymentId = apid.isBlank() ? null : parseInt(apid, 0);
        // counters
        ctx.service.setNextContractId(parseInt(p.getProperty("nextContractId", "1"), 1));
        ctx.service.setNextRecordId(parseInt(p.getProperty("nextRecordId", "1"), 1));
        ctx.service.setNextPaymentId(parseInt(p.getProperty("nextPaymentId", "1"), 1));
        // permissions
        int permCount = parseInt(p.getProperty("perm.count", "0"), 0);
        Map<String, String> perms = new HashMap<>();
        for (int i = 0; i < permCount; i++) {
            String uid = p.getProperty("perm." + i + ".userId", "");
            String perm = p.getProperty("perm." + i + ".permission", "");
            if (!uid.isBlank()) perms.put(uid, perm);
        }
        ctx.systemAdmin.replaceUserPermissions(perms);
    }
}
