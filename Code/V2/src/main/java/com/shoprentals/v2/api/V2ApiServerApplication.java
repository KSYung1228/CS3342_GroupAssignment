package com.shoprentals.v2.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.shoprentals.v2.model.*;
import com.shoprentals.v2.pattern.factory.*;
import com.shoprentals.v2.pattern.observer.*;
import com.shoprentals.v2.pattern.strategy.*;
import com.shoprentals.v2.service.ShopRentalService;
import com.sun.net.httpserver.*;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

public class V2ApiServerApplication {
    private static final int PORT = 8080;
    private static final Gson GSON = new Gson();
    private static final Object LOCK = new Object();
    private static final Path STATE_FILE = Path.of("data", "app-state.properties");
    private static final Path FRONTEND_ROOT = Path.of("frontend").toAbsolutePath().normalize();

    static class AppContext {
        final ShopRentalService service;
        final SystemAdmin systemAdmin;
        final List<Shop> shops;
        final LeaseContractFactory leaseFactory;
        final CommissionStrategy commissionStrategy;
        final MemoryAuditLogListener auditLog;
        final Map<String, User> usersByUsername = new HashMap<>();
        final Map<String, String> roleByUsername = new HashMap<>();
        final Map<String, String> passwordByUsername = new HashMap<>();
        final List<LeaseContract> contracts = new ArrayList<>();
        final List<Payment> payments = new ArrayList<>();
        final Map<Integer, Integer> paymentToContractId = new HashMap<>();
        final Map<Integer, String> paymentMonthById = new HashMap<>();
        final Map<Integer, Double> paymentSalesById = new HashMap<>();
        final Map<String, Integer> activeContractIdByUsername = new HashMap<>();
        final List<String> logs = new ArrayList<>();
        int nextUserNumber = 1000;

        AppContext(ShopRentalService service, SystemAdmin admin, List<Shop> shops,
                   LeaseContractFactory factory, CommissionStrategy strategy, MemoryAuditLogListener audit) {
            this.service = service;
            this.systemAdmin = admin;
            this.shops = shops;
            this.leaseFactory = factory;
            this.commissionStrategy = strategy;
            this.auditLog = audit;
        }
    }

    static class MemoryAuditLogListener implements RentalEventListener {
        private final List<String> entries = new ArrayList<>();
        @Override
        public void onEvent(RentalEvent event) {
            String entry = event.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + " | " + event.getType() + " | " + event.getMessage();
            entries.add(0, entry);
            if (entries.size() > 120) entries.remove(entries.size() - 1);
        }
        List<String> getEntries() { return Collections.unmodifiableList(entries); }
    }

    static class BadRequestException extends RuntimeException { BadRequestException(String m) { super(m); } }
    static class UnauthorizedException extends RuntimeException { UnauthorizedException(String m) { super(m); } }
    static class NotFoundException extends RuntimeException { NotFoundException(String m) { super(m); } }

    private static AppContext appContext = createDefaultContext();

    public static void main(String[] args) throws IOException {
        loadStateQuietly(appContext);
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api", V2ApiServerApplication::handleApi);
        server.createContext("/", V2ApiServerApplication::handleFrontend);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("V2 API server started on http://localhost:" + PORT);
        System.out.println("Open UI at http://localhost:" + PORT);
    }

    private static void handleFrontend(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendText(ex, 405, "Method not allowed", "text/plain; charset=UTF-8"); return;
        }
        String reqPath = ex.getRequestURI().getPath();
        String rel = (reqPath == null || "/".equals(reqPath)) ? "index.html" : reqPath.substring(1);
        Path file = FRONTEND_ROOT.resolve(rel).normalize();
        if (!file.startsWith(FRONTEND_ROOT) || Files.isDirectory(file) || !Files.exists(file)) {
            sendText(ex, 404, "Not found", "text/plain; charset=UTF-8"); return;
        }
        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentTypeFor(file));
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    private static void handleApi(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        if (!path.startsWith("/api")) { sendJson(ex, 404, Map.of("error", "Not found")); return; }
        String route = path.substring(4);
        if (route.isEmpty()) route = "/";
        if (route.length() > 1 && route.endsWith("/")) route = route.substring(0, route.length() - 1);
        try {
            synchronized (LOCK) { dispatch(ex, method, route); }
        } catch (BadRequestException e)   { sendJson(ex, 400, Map.of("error", e.getMessage())); }
          catch (UnauthorizedException e) { sendJson(ex, 401, Map.of("error", e.getMessage())); }
          catch (NotFoundException e)     { sendJson(ex, 404, Map.of("error", e.getMessage())); }
          catch (Exception e)             { sendJson(ex, 500, Map.of("error", "Server error: " + e.getMessage())); }
    }

    private static void dispatch(HttpExchange ex, String method, String route) throws IOException {
        if ("GET".equals(method) && "/health".equals(route)) { sendJson(ex, 200, Map.of("status", "ok")); return; }
        if ("GET".equals(method) && "/state".equals(route))  { sendJson(ex, 200, buildStateResponse()); return; }
        if ("GET".equals(method) && "/logs".equals(route))   { sendJson(ex, 200, Map.of("logs", new ArrayList<>(appContext.logs))); return; }

        if ("POST".equals(method) && "/auth/login".equals(route)) {
            Map<String, String> body = readStringMap(ex);
            User user = authenticate(required(body, "username"), required(body, "password"));
            String role = appContext.roleByUsername.get(user.getUsername());
            sendJson(ex, 200, Map.of("user", userDto(user, role))); return;
        }
        if ("POST".equals(method) && "/auth/register".equals(route)) {
            Map<String, String> body = readStringMap(ex);
            User user = registerTenant(required(body, "username"), required(body, "password"));
            saveStateQuietly(appContext);
            sendJson(ex, 201, Map.of("user", userDto(user, "tenant"), "message", "Tenant registered")); return;
        }
        if ("POST".equals(method) && "/tenant/request-lease".equals(route)) {
            Map<String, Object> body = readObjectMap(ex);
            LeaseContract c = requestLease(reqStr(body,"username"), reqInt(body,"shopId"), reqDouble(body,"baseRent"));
            saveStateQuietly(appContext);
            sendJson(ex, 200, Map.of("contract", contractDto(c), "message", "Lease request submitted")); return;
        }
        if ("POST".equals(method) && "/tenant/active-contract".equals(route)) {
            Map<String, Object> body = readObjectMap(ex);
            setActiveContract(reqStr(body,"username"), reqInt(body,"contractId"));
            saveStateQuietly(appContext);
            sendJson(ex, 200, Map.of("message", "Active contract updated")); return;
        }
        if ("POST".equals(method) && "/tenant/upload-sales".equals(route)) {
            Map<String, Object> body = readObjectMap(ex);
            Payment p = uploadSales(reqStr(body,"username"), reqStr(body,"month"), reqDouble(body,"sales"));
            saveStateQuietly(appContext);
            sendJson(ex, 200, Map.of("payment", paymentDto(p), "message", "Payment generated")); return;
        }
        if ("POST".equals(method) && "/manager/approve-contract".equals(route)) {
            Map<String, Object> body = readObjectMap(ex);
            approveContract(reqInt(body,"contractId"));
            saveStateQuietly(appContext);
            sendJson(ex, 200, Map.of("message", "Contract approved")); return;
        }
        if ("POST".equals(method) && "/manager/terminate-contract".equals(route)) {
            Map<String, Object> body = readObjectMap(ex);
            terminateContract(reqInt(body,"contractId"));
            saveStateQuietly(appContext);
            sendJson(ex, 200, Map.of("message", "Contract terminated")); return;
        }
        if ("POST".equals(method) && "/accounting/confirm-payment".equals(route)) {
            Map<String, Object> body = readObjectMap(ex);
            confirmPayment(reqInt(body,"paymentId"));
            saveStateQuietly(appContext);
            sendJson(ex, 200, Map.of("message", "Payment confirmed")); return;
        }
        if ("POST".equals(method) && "/reset".equals(route)) {
            appContext = createDefaultContext();
            saveStateQuietly(appContext);
            sendJson(ex, 200, Map.of("message", "Demo data reset")); return;
        }
        if ("GET".equals(method) && "/shops".equals(route)) {
            sendJson(ex, 200, Map.of("shops", shopsDto())); return;
        }
        if ("POST".equals(method) && "/shops".equals(route)) {
            createShop(readObjectMap(ex)); saveStateQuietly(appContext);
            sendJson(ex, 201, Map.of("message", "Shop created")); return;
        }
        if (route.startsWith("/shops/")) {
            int shopId = parseId(route.substring("/shops/".length()));
            if ("PUT".equals(method)) {
                updateShop(shopId, readObjectMap(ex)); saveStateQuietly(appContext);
                sendJson(ex, 200, Map.of("message", "Shop updated")); return;
            }
            if ("DELETE".equals(method)) {
                deleteShop(shopId); saveStateQuietly(appContext);
                sendJson(ex, 200, Map.of("message", "Shop deleted")); return;
            }
        }
        if ("GET".equals(method) && "/admin/users".equals(route)) {
            sendJson(ex, 200, Map.of("users", usersDto())); return;
        }
        if ("POST".equals(method) && "/admin/users".equals(route)) {
            Map<String, String> body = readStringMap(ex);
            User user = createUser(required(body,"role").toLowerCase(), required(body,"username"), required(body,"password"));
            saveStateQuietly(appContext);
            sendJson(ex, 201, Map.of("user", userDto(user, required(body,"role").toLowerCase()), "message", "User created")); return;
        }
        if (route.startsWith("/admin/users/") && route.endsWith("/password") && "PUT".equals(method)) {
            String username = URLDecoder.decode(route.substring("/admin/users/".length(), route.length() - "/password".length()), StandardCharsets.UTF_8);
            Map<String, String> body = readStringMap(ex);
            updatePassword(username, required(body,"password")); saveStateQuietly(appContext);
            sendJson(ex, 200, Map.of("message", "Password updated")); return;
        }
        if (route.startsWith("/admin/users/") && "DELETE".equals(method)) {
            String username = URLDecoder.decode(route.substring("/admin/users/".length()), StandardCharsets.UTF_8);
            deleteUser(username); saveStateQuietly(appContext);
            sendJson(ex, 200, Map.of("message", "User deleted")); return;
        }
        if (route.startsWith("/admin/permissions/") && "PUT".equals(method)) {
            String userId = URLDecoder.decode(route.substring("/admin/permissions/".length()), StandardCharsets.UTF_8);
            Map<String, String> body = readStringMap(ex);
            appContext.systemAdmin.changeUserPermission(userId, required(body,"permission")); saveStateQuietly(appContext);
            sendJson(ex, 200, Map.of("message", "Permission updated")); return;
        }
        sendJson(ex, 404, Map.of("error", "Route not found"));
    }

    private static AppContext createDefaultContext() {
        MemoryAuditLogListener audit = new MemoryAuditLogListener();
        RentalEventBus bus = new RentalEventBus();
        bus.subscribe(audit);
        ShopRentalService service = new ShopRentalService(bus);
        SystemAdmin admin = new SystemAdmin("U001", "admin", "admin123");
        List<Shop> shops = new ArrayList<>();
        shops.add(new Shop(1, "A-101", ShopStatus.OPEN,  35.0, ShopType.FNB,   1, 1, 2, 2));
        shops.add(new Shop(2, "A-102", ShopStatus.CLOSED, 40.0, ShopType.POPUP, 3, 1, 2, 2));
        shops.add(new Shop(3, "B-101", ShopStatus.OPEN,  50.0, ShopType.SOLID,  1, 3, 3, 2));
        shops.add(new Shop(4, "B-102", ShopStatus.OPEN,  30.0, ShopType.FNB,    4, 3, 2, 2));
        AppContext ctx = new AppContext(service, admin, shops, new StandardLeaseFactory(), new ProgressiveCommissionStrategy(), audit);
        registerUser(ctx, admin, "systemadmin", "admin123");
        registerUser(ctx, admin.createAccount("accounting", "U100", "acct", "pass"), "accounting", "pass");
        registerUser(ctx, admin.createAccount("tenant", "U200", "freshMart", "pass"), "tenant", "pass");
        registerUser(ctx, admin.createAccount("contractmanager", "U300", "cm", "pass"), "contractmanager", "pass");
        ctx.logs.add(0, now() + " | System initialized with V2 sample data");
        return ctx;
    }

    private static User authenticate(String username, String password) {
        User user = appContext.usersByUsername.get(username);
        if (user == null) throw new UnauthorizedException("Invalid username or password");
        String expected = appContext.passwordByUsername.get(username);
        if (expected == null || !expected.equals(password)) throw new UnauthorizedException("Invalid username or password");
        if (!user.login(password)) throw new UnauthorizedException("Invalid username or password");
        return user;
    }

    private static User registerTenant(String username, String password) {
        if (username.isBlank() || password.isBlank()) throw new BadRequestException("Username and password are required");
        if (appContext.usersByUsername.containsKey(username)) throw new BadRequestException("Username already exists");
        String uid = "U" + (++appContext.nextUserNumber);
        User user = appContext.systemAdmin.createAccount("tenant", uid, username, password);
        registerUser(appContext, user, "tenant", password);
        appContext.logs.add(0, now() + " | Tenant account created: " + username);
        return user;
    }

    private static LeaseContract requestLease(String username, int shopId, double baseRent) {
        if (baseRent <= 0) throw new BadRequestException("Base rent must be greater than zero");
        User u = appContext.usersByUsername.get(username);
        if (!(u instanceof Tenant tenant)) throw new BadRequestException("Tenant account required");
        Shop shop = findShopById(shopId);
        if (shop == null) throw new NotFoundException("Shop not found");
        if (shop.getStatus() != ShopStatus.OPEN) throw new BadRequestException("Shop is not open");
        LeaseContract c = tenant.requestRentStore(shop, appContext.service, appContext.leaseFactory, baseRent);
        appContext.contracts.add(c);
        appContext.activeContractIdByUsername.put(username, c.getContractId());
        appContext.logs.add(0, now() + " | Lease requested by " + username + " for shop " + shop.getShopNum());
        return c;
    }

    private static void setActiveContract(String username, int contractId) {
        LeaseContract c = findContractById(contractId);
        if (c == null) throw new NotFoundException("Contract not found");
        String tenantName = findUsernameByUser(c.getTenant());
        if (!username.equals(tenantName)) throw new BadRequestException("Contract does not belong to current tenant");
        appContext.activeContractIdByUsername.put(username, contractId);
        appContext.logs.add(0, now() + " | Active contract set for " + username + ": " + contractId);
    }

    private static void approveContract(int contractId) {
        LeaseContract c = findContractById(contractId);
        if (c == null) throw new NotFoundException("Contract not found");
        ContractManager mgr = getFirstByRole("contractmanager", ContractManager.class);
        if (mgr == null) throw new BadRequestException("No contract manager found");
        if (!mgr.permitRequest(c)) throw new BadRequestException("Only pending contracts can be approved");
        mgr.applyApproveContractService(c, appContext.service);
        c.getTenant().signContract(c);
        appContext.logs.add(0, now() + " | Contract approved: " + contractId);
    }

    private static void terminateContract(int contractId) {
        LeaseContract c = findContractById(contractId);
        if (c == null) throw new NotFoundException("Contract not found");
        if (c.getStatus() != ContractStatus.ACTIVE && c.getStatus() != ContractStatus.PENDING_APPROVAL)
            throw new BadRequestException("Contract cannot be terminated from state: " + c.getStatus());
        c.terminate();
        appContext.logs.add(0, now() + " | Contract terminated: " + contractId);
    }

    private static Payment uploadSales(String username, String month, double sales) {
        if (month.isBlank()) throw new BadRequestException("Month is required");
        if (sales < 0) throw new BadRequestException("Sales must be non-negative");
        User u = appContext.usersByUsername.get(username);
        if (!(u instanceof Tenant tenant)) throw new BadRequestException("Tenant account required");
        Integer cid = appContext.activeContractIdByUsername.get(username);
        if (cid == null) throw new BadRequestException("No active contract selected");
        LeaseContract c = findContractById(cid);
        if (c == null) throw new NotFoundException("Active contract not found");
        if (c.getStatus() != ContractStatus.ACTIVE) throw new BadRequestException("Contract must be active before uploading sales");
        if (c.getTenant() != tenant) throw new BadRequestException("Active contract does not belong to current tenant");
        Payment p = tenant.uploadMonthlyRecord(c, month, sales, appContext.commissionStrategy, appContext.service);
        appContext.payments.add(p);
        appContext.paymentToContractId.put(p.getPaymentId(), c.getContractId());
        appContext.paymentMonthById.put(p.getPaymentId(), month);
        appContext.paymentSalesById.put(p.getPaymentId(), sales);
        appContext.logs.add(0, now() + " | Monthly sales uploaded for contract " + c.getContractId() + ", payment " + p.getPaymentId());
        return p;
    }

    private static void confirmPayment(int paymentId) {
        Payment p = findPaymentById(paymentId);
        if (p == null) throw new NotFoundException("Payment not found");
        Accounting acct = getFirstByRole("accounting", Accounting.class);
        if (acct == null) throw new BadRequestException("No accounting user found");
        if (!acct.confirmPaymentTransfer(p)) throw new BadRequestException("Payment cannot be confirmed");
        appContext.logs.add(0, now() + " | Payment confirmed: " + paymentId);
    }

    private static void createShop(Map<String, Object> body) {
        String num = reqStr(body, "shopNum");
        double area = reqDouble(body, "area");
        if (area <= 0) throw new BadRequestException("Area must be greater than zero");
        ShopType type = parseShopType(reqStr(body, "type"));
        if (type == null) throw new BadRequestException("Invalid shop type");
        int nextId = appContext.shops.stream().mapToInt(Shop::getShopId).max().orElse(0) + 1;
        int px = body.containsKey("posX") ? (int) Math.round(((Number) body.get("posX")).doubleValue()) : 0;
        int py = body.containsKey("posY") ? (int) Math.round(((Number) body.get("posY")).doubleValue()) : 0;
        int pw = body.containsKey("width") ? (int) Math.round(((Number) body.get("width")).doubleValue()) : 2;
        int ph = body.containsKey("height") ? (int) Math.round(((Number) body.get("height")).doubleValue()) : 2;
        Shop shop = new Shop(nextId, num, ShopStatus.OPEN, area, type, px, py, pw, ph);
        appContext.shops.add(shop);
        appContext.logs.add(0, now() + " | Shop created: " + num);
    }

    private static void updateShop(int shopId, Map<String, Object> body) {
        Shop shop = findShopById(shopId);
        if (shop == null) throw new NotFoundException("Shop not found");
        String num = reqStr(body, "shopNum");
        double area = reqDouble(body, "area");
        if (area <= 0) throw new BadRequestException("Area must be greater than zero");
        ShopType type = parseShopType(reqStr(body, "type"));
        if (type == null) throw new BadRequestException("Invalid shop type");
        ShopStatus status = parseShopStatus(reqStr(body, "status"));
        if (status == null) throw new BadRequestException("Invalid shop status");
        ShopStatus prev = shop.getStatus();
        shop.editStoreInfo(num, area, type);
        shop.updateStoreStatus(status);
        if (body.containsKey("posX")) {
            int px = (int) Math.round(((Number) body.get("posX")).doubleValue());
            int py = (int) Math.round(((Number) body.get("posY")).doubleValue());
            int pw = (int) Math.round(((Number) body.get("width")).doubleValue());
            int ph = (int) Math.round(((Number) body.get("height")).doubleValue());
            shop.setPosition(px, py, pw, ph);
        }
        if (status != prev && (status == ShopStatus.CLOSED || status == ShopStatus.UNDER_REPAIR)) {
            Accounting acct = getFirstByRole("accounting", Accounting.class);
            if (acct != null)
                for (LeaseContract c : appContext.contracts)
                    if (c.getShop().getShopId() == shopId) acct.alterRentByStoreStatus(c, shop);
        }
        appContext.logs.add(0, now() + " | Shop updated: " + shop.getShopNum() + " => " + shop.getStatus());
    }

    private static void deleteShop(int shopId) {
        Shop shop = findShopById(shopId);
        if (shop == null) throw new NotFoundException("Shop not found");
        for (LeaseContract c : appContext.contracts)
            if (c.getShop().getShopId() == shopId) throw new BadRequestException("Cannot delete shop with existing contracts");
        appContext.shops.remove(shop);
        appContext.logs.add(0, now() + " | Shop deleted: " + shop.getShopNum());
    }

    private static User createUser(String role, String username, String password) {
        if (!List.of("tenant","accounting","contractmanager").contains(role)) throw new BadRequestException("Unsupported role");
        if (username.isBlank() || password.isBlank()) throw new BadRequestException("Username and password are required");
        if (appContext.usersByUsername.containsKey(username)) throw new BadRequestException("Username already exists");
        String uid = "U" + (++appContext.nextUserNumber);
        User user = appContext.systemAdmin.createAccount(role, uid, username, password);
        registerUser(appContext, user, role, password);
        appContext.logs.add(0, now() + " | User created: " + username + " (" + role + ")");
        return user;
    }

    private static void updatePassword(String username, String newPassword) {
        if (newPassword.isBlank()) throw new BadRequestException("Password cannot be empty");
        User user = appContext.usersByUsername.get(username);
        if (user == null) throw new NotFoundException("User not found");
        user.changePassword(newPassword);
        appContext.passwordByUsername.put(username, newPassword);
        appContext.logs.add(0, now() + " | Password updated for " + username);
    }

    private static void deleteUser(String username) {
        User user = appContext.usersByUsername.get(username);
        if (user == null) throw new NotFoundException("User not found");
        if ("systemadmin".equals(appContext.roleByUsername.get(username))) throw new BadRequestException("Cannot delete admin account");
        if (user instanceof Tenant t)
            for (LeaseContract c : appContext.contracts)
                if (c.getTenant() == t) throw new BadRequestException("Cannot delete tenant with existing contracts");
        appContext.usersByUsername.remove(username);
        appContext.roleByUsername.remove(username);
        appContext.passwordByUsername.remove(username);
        appContext.logs.add(0, now() + " | User deleted: " + username);
    }

    private static Map<String, Object> buildStateResponse() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("shops", shopsDto());
        m.put("contracts", contractsDto());
        m.put("payments", paymentsDto());
        m.put("users", usersDto());
        m.put("activeContractIdByUser", new HashMap<>(appContext.activeContractIdByUsername));
        m.put("permissions", appContext.systemAdmin.getUserPermissionsSnapshot());
        List<String> combined = new ArrayList<>(appContext.auditLog.getEntries());
        combined.addAll(appContext.logs);
        m.put("logs", combined);
        return m;
    }

    private static List<Map<String, Object>> shopsDto() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Shop s : appContext.shops) list.add(shopDto(s));
        return list;
    }

    private static Map<String, Object> shopDto(Shop s) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("shopId", s.getShopId());
        m.put("shopNum", s.getShopNum());
        m.put("status", s.getStatus().name());
        m.put("area", s.getArea());
        m.put("type", s.getType().name());
        m.put("posX", s.getPosX());
        m.put("posY", s.getPosY());
        m.put("width", s.getWidth());
        m.put("height", s.getHeight());
        return m;
    }

    private static List<Map<String, Object>> contractsDto() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LeaseContract c : appContext.contracts) list.add(contractDto(c));
        return list;
    }

    private static Map<String, Object> contractDto(LeaseContract c) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("contractId", c.getContractId());
        m.put("shopId", c.getShop().getShopId());
        m.put("shopNum", c.getShop().getShopNum());
        m.put("tenant", c.getTenant().getUsername());
        m.put("baseRent", c.getBaseRent());
        m.put("commissionRate", c.getCommissionRate());
        m.put("status", c.getStatus().name());
        return m;
    }

    private static List<Map<String, Object>> paymentsDto() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Payment p : appContext.payments) list.add(paymentDto(p));
        return list;
    }

    private static Map<String, Object> paymentDto(Payment p) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("paymentId", p.getPaymentId());
        m.put("amount", p.getAmount());
        m.put("status", p.getStatus().name());
        m.put("contractId", appContext.paymentToContractId.getOrDefault(p.getPaymentId(), 0));
        m.put("month", appContext.paymentMonthById.getOrDefault(p.getPaymentId(), ""));
        m.put("sales", appContext.paymentSalesById.getOrDefault(p.getPaymentId(), 0.0));
        return m;
    }

    private static List<Map<String, Object>> usersDto() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, User> e : appContext.usersByUsername.entrySet()) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("username", e.getKey());
            m.put("userId", e.getValue().getUserId());
            m.put("role", appContext.roleByUsername.getOrDefault(e.getKey(), ""));
            list.add(m);
        }
        return list;
    }

    private static Map<String, Object> userDto(User u, String role) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("username", u.getUsername());
        m.put("userId", u.getUserId());
        m.put("role", role);
        return m;
    }

    private static Shop findShopById(int id) {
        return appContext.shops.stream().filter(s -> s.getShopId() == id).findFirst().orElse(null);
    }

    private static LeaseContract findContractById(int id) {
        return appContext.contracts.stream().filter(c -> c.getContractId() == id).findFirst().orElse(null);
    }

    private static Payment findPaymentById(int id) {
        return appContext.payments.stream().filter(p -> p.getPaymentId() == id).findFirst().orElse(null);
    }

    private static String findUsernameByUser(User user) {
        for (Map.Entry<String, User> e : appContext.usersByUsername.entrySet())
            if (e.getValue() == user) return e.getKey();
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getFirstByRole(String role, Class<T> clazz) {
        for (Map.Entry<String, User> e : appContext.usersByUsername.entrySet())
            if (role.equals(appContext.roleByUsername.get(e.getKey())) && clazz.isInstance(e.getValue()))
                return (T) e.getValue();
        return null;
    }

    private static void registerUser(AppContext ctx, User user, String role, String password) {
        ctx.usersByUsername.put(user.getUsername(), user);
        ctx.roleByUsername.put(user.getUsername(), role);
        ctx.passwordByUsername.put(user.getUsername(), password);
    }

    private static ShopType parseShopType(String s) {
        return switch (s.toUpperCase().replace("&","").replace(" ","")) {
            case "FNB","F&B","FB" -> ShopType.FNB;
            case "POPUP"          -> ShopType.POPUP;
            case "SOLID"          -> ShopType.SOLID;
            default               -> null;
        };
    }

    private static ShopStatus parseShopStatus(String s) {
        try { return ShopStatus.valueOf(s.toUpperCase()); } catch (Exception e) { return null; }
    }

    private static int parseId(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; }
    }

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static Map<String, String> readStringMap(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Type t = new TypeToken<Map<String, String>>(){}.getType();
        Map<String, String> m = GSON.fromJson(body, t);
        return m != null ? m : new HashMap<>();
    }

    private static Map<String, Object> readObjectMap(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Type t = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> m = GSON.fromJson(body, t);
        return m != null ? m : new HashMap<>();
    }

    private static String required(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null || v.isBlank()) throw new BadRequestException("Missing field: " + key);
        return v;
    }

    private static String reqStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) throw new BadRequestException("Missing field: " + key);
        return v.toString();
    }

    private static int reqInt(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) throw new BadRequestException("Missing field: " + key);
        return (int) Math.round(((Number) v).doubleValue());
    }

    private static double reqDouble(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) throw new BadRequestException("Missing field: " + key);
        return ((Number) v).doubleValue();
    }

    private static void sendJson(HttpExchange ex, int code, Object body) throws IOException {
        byte[] data = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(code, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    private static void sendText(HttpExchange ex, int code, String body, String ct) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.sendResponseHeaders(code, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    private static void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String contentTypeFor(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=UTF-8";
        if (name.endsWith(".css"))  return "text/css; charset=UTF-8";
        if (name.endsWith(".js"))   return "application/javascript; charset=UTF-8";
        if (name.endsWith(".json")) return "application/json; charset=UTF-8";
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".svg"))  return "image/svg+xml";
        return "application/octet-stream";
    }

    private static void saveStateQuietly(AppContext ctx) {
        try { saveState(ctx); } catch (Exception ignored) {}
    }

    private static void loadStateQuietly(AppContext ctx) {
        try {
            if (!Files.exists(STATE_FILE)) { System.out.println("No saved state. Using default data."); return; }
            loadState(ctx);
            System.out.println("State loaded from " + STATE_FILE);
        } catch (Exception e) { System.out.println("Failed to load state: " + e.getMessage()); }
    }

    private static void saveState(AppContext ctx) throws IOException {
        Properties p = new Properties();
        p.setProperty("shop.count", String.valueOf(ctx.shops.size()));
        for (int i = 0; i < ctx.shops.size(); i++) {
            Shop s = ctx.shops.get(i); String pfx = "shop." + i + ".";
            p.setProperty(pfx+"id", String.valueOf(s.getShopId()));
            p.setProperty(pfx+"num", s.getShopNum());
            p.setProperty(pfx+"status", s.getStatus().name());
            p.setProperty(pfx+"area", String.valueOf(s.getArea()));
            p.setProperty(pfx+"type", s.getType().name());
            p.setProperty(pfx+"posX", String.valueOf(s.getPosX()));
            p.setProperty(pfx+"posY", String.valueOf(s.getPosY()));
            p.setProperty(pfx+"width", String.valueOf(s.getWidth()));
            p.setProperty(pfx+"height", String.valueOf(s.getHeight()));
        }
        p.setProperty("user.count", String.valueOf(ctx.usersByUsername.size()));
        int ui = 0;
        for (Map.Entry<String, User> e : ctx.usersByUsername.entrySet()) {
            String pfx = "user." + ui + ".";
            p.setProperty(pfx+"username", e.getKey());
            p.setProperty(pfx+"userId", e.getValue().getUserId());
            p.setProperty(pfx+"role", ctx.roleByUsername.getOrDefault(e.getKey(),""));
            p.setProperty(pfx+"password", ctx.passwordByUsername.getOrDefault(e.getKey(),""));
            ui++;
        }
        p.setProperty("nextUserNumber", String.valueOf(ctx.nextUserNumber));
        p.setProperty("contract.count", String.valueOf(ctx.contracts.size()));
        for (int i = 0; i < ctx.contracts.size(); i++) {
            LeaseContract c = ctx.contracts.get(i); String pfx = "contract." + i + ".";
            p.setProperty(pfx+"id", String.valueOf(c.getContractId()));
            p.setProperty(pfx+"shopId", String.valueOf(c.getShop().getShopId()));
            p.setProperty(pfx+"tenant", c.getTenant().getUsername());
            p.setProperty(pfx+"baseRent", String.valueOf(c.getBaseRent()));
            p.setProperty(pfx+"commissionRate", String.valueOf(c.getCommissionRate()));
            p.setProperty(pfx+"status", c.getStatus().name());
        }
        p.setProperty("payment.count", String.valueOf(ctx.payments.size()));
        for (int i = 0; i < ctx.payments.size(); i++) {
            Payment pay = ctx.payments.get(i); String pfx = "payment." + i + ".";
            p.setProperty(pfx+"id", String.valueOf(pay.getPaymentId()));
            p.setProperty(pfx+"amount", String.valueOf(pay.getAmount()));
            p.setProperty(pfx+"status", pay.getStatus().name());
            p.setProperty(pfx+"contractId", String.valueOf(ctx.paymentToContractId.getOrDefault(pay.getPaymentId(),0)));
            p.setProperty(pfx+"month", ctx.paymentMonthById.getOrDefault(pay.getPaymentId(),""));
            p.setProperty(pfx+"sales", String.valueOf(ctx.paymentSalesById.getOrDefault(pay.getPaymentId(),0.0)));
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : ctx.activeContractIdByUsername.entrySet())
            sb.append(e.getKey()).append("=").append(e.getValue()).append(";");
        p.setProperty("activeContracts", sb.toString());
        p.setProperty("nextContractId", String.valueOf(ctx.service.getNextContractId()));
        p.setProperty("nextRecordId", String.valueOf(ctx.service.getNextRecordId()));
        p.setProperty("nextPaymentId", String.valueOf(ctx.service.getNextPaymentId()));
        Map<String, String> perms = ctx.systemAdmin.getUserPermissionsSnapshot();
        p.setProperty("perm.count", String.valueOf(perms.size()));
        int pi = 0;
        for (Map.Entry<String, String> e : perms.entrySet()) {
            p.setProperty("perm."+pi+".userId", e.getKey());
            p.setProperty("perm."+pi+".permission", e.getValue());
            pi++;
        }
        Files.createDirectories(STATE_FILE.getParent());
        try (OutputStream os = Files.newOutputStream(STATE_FILE)) { p.store(os, "V2 App State"); }
    }

    private static void loadState(AppContext ctx) throws IOException {
        Properties p = new Properties();
        try (InputStream is = Files.newInputStream(STATE_FILE)) { p.load(is); }
        int sc = parseInt(p.getProperty("shop.count","0"));
        ctx.shops.clear();
        for (int i = 0; i < sc; i++) {
            String pfx = "shop."+i+".";
            ctx.shops.add(new Shop(
                parseInt(p.getProperty(pfx+"id","0")), p.getProperty(pfx+"num",""),
                ShopStatus.valueOf(p.getProperty(pfx+"status","OPEN")),
                Double.parseDouble(p.getProperty(pfx+"area","0")),
                ShopType.valueOf(p.getProperty(pfx+"type","SOLID")),
                parseInt(p.getProperty(pfx+"posX","0")), parseInt(p.getProperty(pfx+"posY","0")),
                parseInt(p.getProperty(pfx+"width","2")), parseInt(p.getProperty(pfx+"height","2"))
            ));
        }
        int uc = parseInt(p.getProperty("user.count","0"));
        ctx.usersByUsername.clear(); ctx.roleByUsername.clear(); ctx.passwordByUsername.clear();
        for (int i = 0; i < uc; i++) {
            String pfx = "user."+i+".";
            String username = p.getProperty(pfx+"username","");
            String userId = p.getProperty(pfx+"userId","");
            String role = p.getProperty(pfx+"role","");
            String password = p.getProperty(pfx+"password","");
            User user = switch (role) {
                case "systemadmin"     -> new SystemAdmin(userId, username, password);
                case "accounting"      -> new Accounting(userId, username, password);
                case "contractmanager" -> new ContractManager(userId, username, password);
                case "tenant"          -> new Tenant(userId, username, password, username+" contact");
                default -> null;
            };
            if (user != null) registerUser(ctx, user, role, password);
        }
        ctx.nextUserNumber = parseInt(p.getProperty("nextUserNumber","1000"));
        int cc = parseInt(p.getProperty("contract.count","0"));
        ctx.contracts.clear();
        for (int i = 0; i < cc; i++) {
            String pfx = "contract."+i+".";
            int id = parseInt(p.getProperty(pfx+"id","0"));
            int shopId = parseInt(p.getProperty(pfx+"shopId","0"));
            String tenantName = p.getProperty(pfx+"tenant","");
            double baseRent = Double.parseDouble(p.getProperty(pfx+"baseRent","0"));
            double commRate = Double.parseDouble(p.getProperty(pfx+"commissionRate","0.08"));
            ContractStatus status = ContractStatus.valueOf(p.getProperty(pfx+"status","DRAFT"));
            Shop shop = findShopById(shopId);
            User u = ctx.usersByUsername.get(tenantName);
            if (shop != null && u instanceof Tenant tenant) {
                LeaseContract c = new LeaseContract(id, shop, tenant, baseRent, commRate);
                c.restoreStatus(status);
                ctx.contracts.add(c);
            }
        }
        int pc = parseInt(p.getProperty("payment.count","0"));
        ctx.payments.clear();
        for (int i = 0; i < pc; i++) {
            String pfx = "payment."+i+".";
            int id = parseInt(p.getProperty(pfx+"id","0"));
            double amount = Double.parseDouble(p.getProperty(pfx+"amount","0"));
            PaymentStatus ps = PaymentStatus.valueOf(p.getProperty(pfx+"status","UNVERIFIED"));
            int cid = parseInt(p.getProperty(pfx+"contractId","0"));
            String month = p.getProperty(pfx+"month","");
            double sales = Double.parseDouble(p.getProperty(pfx+"sales","0"));
            ctx.payments.add(new Payment(id, amount, ps));
            ctx.paymentToContractId.put(id, cid);
            ctx.paymentMonthById.put(id, month);
            ctx.paymentSalesById.put(id, sales);
        }
        String ac = p.getProperty("activeContracts","");
        ctx.activeContractIdByUsername.clear();
        if (!ac.isBlank())
            for (String part : ac.split(";")) {
                String[] kv = part.split("=");
                if (kv.length == 2) ctx.activeContractIdByUsername.put(kv[0], parseInt(kv[1]));
            }
        ctx.service.setNextContractId(parseInt(p.getProperty("nextContractId","1")));
        ctx.service.setNextRecordId(parseInt(p.getProperty("nextRecordId","1")));
        ctx.service.setNextPaymentId(parseInt(p.getProperty("nextPaymentId","1")));
        int permCount = parseInt(p.getProperty("perm.count","0"));
        Map<String, String> perms = new HashMap<>();
        for (int i = 0; i < permCount; i++) {
            String uid = p.getProperty("perm."+i+".userId","");
            String perm = p.getProperty("perm."+i+".permission","");
            if (!uid.isBlank()) perms.put(uid, perm);
        }
        ctx.systemAdmin.replaceUserPermissions(perms);
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}
