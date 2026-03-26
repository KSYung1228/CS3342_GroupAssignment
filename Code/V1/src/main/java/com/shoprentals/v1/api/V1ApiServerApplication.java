package com.shoprentals.v1.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.shoprentals.v1.model.Accounting;
import com.shoprentals.v1.model.ContractManager;
import com.shoprentals.v1.model.ContractStatus;
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
import com.shoprentals.v1.pattern.observer.RentalEvent;
import com.shoprentals.v1.pattern.observer.RentalEventBus;
import com.shoprentals.v1.pattern.observer.RentalEventListener;
import com.shoprentals.v1.pattern.strategy.CommissionStrategy;
import com.shoprentals.v1.pattern.strategy.ProgressiveCommissionStrategy;
import com.shoprentals.v1.service.ShopRentalService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;

public class V1ApiServerApplication {
    private static final int PORT = 8080;
    private static final Gson GSON = new Gson();
    private static final Object LOCK = new Object();
    private static final Path STATE_FILE = Path.of("data", "app-state.properties");
    private static final Path FRONTEND_ROOT = Path.of("frontend").toAbsolutePath().normalize();

    private static AppContext appContext = createDefaultContext();

    public static void main(String[] args) throws IOException {
        loadStateQuietly(appContext);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api", V1ApiServerApplication::handleApi);
        server.createContext("/", V1ApiServerApplication::handleFrontend);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("V1 API server started on http://localhost:" + PORT);
        System.out.println("Open UI at http://localhost:" + PORT);
    }

    private static void handleFrontend(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed", "text/plain; charset=UTF-8");
            return;
        }

        String requestPath = exchange.getRequestURI().getPath();
        String relativePath = (requestPath == null || "/".equals(requestPath))
                ? "index.html"
                : requestPath.substring(1);

        Path filePath = FRONTEND_ROOT.resolve(relativePath).normalize();
        if (!filePath.startsWith(FRONTEND_ROOT) || Files.isDirectory(filePath) || !Files.exists(filePath)) {
            sendText(exchange, 404, "Not found", "text/plain; charset=UTF-8");
            return;
        }

        byte[] data = Files.readAllBytes(filePath);
        exchange.getResponseHeaders().set("Content-Type", contentTypeFor(filePath));
        exchange.sendResponseHeaders(200, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private static void handleApi(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (!path.startsWith("/api")) {
            sendJson(exchange, 404, Map.of("error", "Not found"));
            return;
        }

        String route = path.substring(4);
        if (route.isEmpty()) {
            route = "/";
        }
        if (route.length() > 1 && route.endsWith("/")) {
            route = route.substring(0, route.length() - 1);
        }

        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());

        try {
            synchronized (LOCK) {
                dispatch(exchange, method, route, query);
            }
        } catch (BadRequestException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (UnauthorizedException e) {
            sendJson(exchange, 401, Map.of("error", e.getMessage()));
        } catch (NotFoundException e) {
            sendJson(exchange, 404, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("error", "Server error: " + e.getMessage()));
        }
    }

    private static void dispatch(HttpExchange exchange, String method, String route, Map<String, String> query)
            throws IOException {
        if ("GET".equals(method) && "/health".equals(route)) {
            sendJson(exchange, 200, Map.of("status", "ok"));
            return;
        }

        if ("GET".equals(method) && "/state".equals(route)) {
            sendJson(exchange, 200, buildStateResponse());
            return;
        }

        if ("GET".equals(method) && "/logs".equals(route)) {
            sendJson(exchange, 200, Map.of("logs", new ArrayList<>(appContext.logs)));
            return;
        }

        if ("POST".equals(method) && "/auth/login".equals(route)) {
            Map<String, String> body = readStringMapBody(exchange);
            String username = required(body, "username");
            String password = required(body, "password");
            User user = authenticate(username, password);
            String role = appContext.roleByUsername.get(username);
            sendJson(exchange, 200, Map.of("user", userDto(user, role)));
            return;
        }

        if ("POST".equals(method) && "/auth/register".equals(route)) {
            Map<String, String> body = readStringMapBody(exchange);
            String username = required(body, "username");
            String password = required(body, "password");
            User user = registerTenant(username, password);
            saveStateQuietly(appContext);
            sendJson(exchange, 201, Map.of("user", userDto(user, "tenant"), "message", "Tenant registered"));
            return;
        }

        if ("POST".equals(method) && "/tenant/request-lease".equals(route)) {
            Map<String, Object> body = readObjectMapBody(exchange);
            String username = requiredString(body, "username");
            int shopId = requiredInt(body, "shopId");
            double baseRent = requiredDouble(body, "baseRent");
            LeaseContract contract = requestLease(username, shopId, baseRent);
            saveStateQuietly(appContext);
            sendJson(exchange, 200, Map.of("contract", contractDto(contract), "message", "Lease request submitted"));
            return;
        }

        if ("POST".equals(method) && "/tenant/active-contract".equals(route)) {
            Map<String, Object> body = readObjectMapBody(exchange);
            String username = requiredString(body, "username");
            int contractId = requiredInt(body, "contractId");
            setActiveContract(username, contractId);
            saveStateQuietly(appContext);
            sendJson(exchange, 200, Map.of("message", "Active contract updated"));
            return;
        }

        if ("POST".equals(method) && "/manager/approve-contract".equals(route)) {
            Map<String, Object> body = readObjectMapBody(exchange);
            int contractId = requiredInt(body, "contractId");
            approveContract(contractId);
            saveStateQuietly(appContext);
            sendJson(exchange, 200, Map.of("message", "Contract approved"));
            return;
        }

        if ("POST".equals(method) && "/tenant/upload-sales".equals(route)) {
            Map<String, Object> body = readObjectMapBody(exchange);
            String username = requiredString(body, "username");
            String month = requiredString(body, "month");
            double sales = requiredDouble(body, "sales");
            Payment payment = uploadSales(username, month, sales);
            saveStateQuietly(appContext);
            sendJson(exchange, 200, Map.of("payment", paymentDto(payment), "message", "Payment generated"));
            return;
        }

        if ("POST".equals(method) && "/accounting/confirm-payment".equals(route)) {
            Map<String, Object> body = readObjectMapBody(exchange);
            int paymentId = requiredInt(body, "paymentId");
            confirmPayment(paymentId);
            saveStateQuietly(appContext);
            sendJson(exchange, 200, Map.of("message", "Payment confirmed"));
            return;
        }

        if ("POST".equals(method) && "/reset".equals(route)) {
            appContext = createDefaultContext();
            saveStateQuietly(appContext);
            sendJson(exchange, 200, Map.of("message", "Demo data reset"));
            return;
        }

        if ("GET".equals(method) && "/shops".equals(route)) {
            sendJson(exchange, 200, Map.of("shops", shopsDto()));
            return;
        }

        if ("POST".equals(method) && "/shops".equals(route)) {
            Map<String, Object> body = readObjectMapBody(exchange);
            createShop(body);
            saveStateQuietly(appContext);
            sendJson(exchange, 201, Map.of("message", "Shop created"));
            return;
        }

        if (route.startsWith("/shops/")) {
            int shopId = parseId(route.substring("/shops/".length()));
            if ("PUT".equals(method)) {
                Map<String, Object> body = readObjectMapBody(exchange);
                updateShop(shopId, body);
                saveStateQuietly(appContext);
                sendJson(exchange, 200, Map.of("message", "Shop updated"));
                return;
            }
            if ("DELETE".equals(method)) {
                deleteShop(shopId);
                saveStateQuietly(appContext);
                sendJson(exchange, 200, Map.of("message", "Shop deleted"));
                return;
            }
        }

        if ("GET".equals(method) && "/admin/users".equals(route)) {
            sendJson(exchange, 200, Map.of("users", usersDto()));
            return;
        }

        if ("POST".equals(method) && "/admin/users".equals(route)) {
            Map<String, String> body = readStringMapBody(exchange);
            String role = required(body, "role").toLowerCase();
            String username = required(body, "username");
            String password = required(body, "password");
            User user = createUser(role, username, password);
            saveStateQuietly(appContext);
            sendJson(exchange, 201, Map.of("user", userDto(user, role), "message", "User created"));
            return;
        }

        if (route.startsWith("/admin/users/") && route.endsWith("/password") && "PUT".equals(method)) {
            String encoded = route.substring("/admin/users/".length(), route.length() - "/password".length());
            String username = decodePathSegment(encoded);
            Map<String, String> body = readStringMapBody(exchange);
            String password = required(body, "password");
            updatePassword(username, password);
            saveStateQuietly(appContext);
            sendJson(exchange, 200, Map.of("message", "Password updated"));
            return;
        }

        if (route.startsWith("/admin/users/") && "DELETE".equals(method)) {
            String encoded = route.substring("/admin/users/".length());
            String username = decodePathSegment(encoded);
            deleteUser(username);
            saveStateQuietly(appContext);
            sendJson(exchange, 200, Map.of("message", "User deleted"));
            return;
        }

        if (route.startsWith("/admin/permissions/") && "PUT".equals(method)) {
            String userId = decodePathSegment(route.substring("/admin/permissions/".length()));
            Map<String, String> body = readStringMapBody(exchange);
            String permission = required(body, "permission");
            setPermission(userId, permission);
            saveStateQuietly(appContext);
            sendJson(exchange, 200, Map.of("message", "Permission updated"));
            return;
        }

        sendJson(exchange, 404, Map.of("error", "Route not found"));
    }

    private static AppContext createDefaultContext() {
        RentalEventBus eventBus = new RentalEventBus();
        MemoryAuditLogListener audit = new MemoryAuditLogListener();
        eventBus.subscribe(audit);

        ShopRentalService service = new ShopRentalService(eventBus);
        SystemAdmin systemAdmin = new SystemAdmin("U001", "admin", "admin123");

        List<Shop> shops = new ArrayList<>();
        shops.add(new Shop(1, "A-101", ShopStatus.OPEN, 35.0, ShopType.FNB));
        shops.add(new Shop(2, "A-102", ShopStatus.CLOSED, 40.0, ShopType.POPUP));

        AppContext ctx = new AppContext(
                service,
                systemAdmin,
                shops,
                new StandardLeaseFactory(),
                new ProgressiveCommissionStrategy(),
                audit);

        registerUser(ctx, systemAdmin, "systemadmin", "admin123");
        registerUser(ctx, systemAdmin.createAccount("accounting", "U100", "acct", "pass"), "accounting", "pass");
        registerUser(ctx, systemAdmin.createAccount("tenant", "U200", "freshMart", "pass"), "tenant", "pass");
        registerUser(ctx, systemAdmin.createAccount("contractmanager", "U300", "cm", "pass"), "contractmanager",
                "pass");

        ctx.logs.add(0, now() + " | System initialized with V1 sample data");
        return ctx;
    }

    private static User authenticate(String username, String password) {
        User user = appContext.usersByUsername.get(username);
        if (user == null) {
            throw new UnauthorizedException("Invalid username or password");
        }

        String expectedPassword = appContext.passwordByUsername.get(username);
        if (expectedPassword == null || !expectedPassword.equals(password)) {
            throw new UnauthorizedException("Invalid username or password");
        }

        if (!user.login(password)) {
            throw new UnauthorizedException("Invalid username or password");
        }

        return user;
    }

    private static User registerTenant(String username, String password) {
        if (username.isBlank() || password.isBlank()) {
            throw new BadRequestException("Username and password are required");
        }
        if (appContext.usersByUsername.containsKey(username)) {
            throw new BadRequestException("Username already exists");
        }

        String userId = generateUserId(appContext);
        User user = appContext.systemAdmin.createAccount("tenant", userId, username, password);
        registerUser(appContext, user, "tenant", password);
        appContext.logs.add(0, now() + " | Tenant account created: " + username);
        return user;
    }

    private static LeaseContract requestLease(String username, int shopId, double baseRent) {
        if (baseRent <= 0) {
            throw new BadRequestException("Base rent must be greater than zero");
        }

        User user = appContext.usersByUsername.get(username);
        if (!(user instanceof Tenant tenant)) {
            throw new BadRequestException("Tenant account is required");
        }

        Shop shop = findShopById(shopId);
        if (shop == null) {
            throw new NotFoundException("Shop not found");
        }
        if (shop.getStatus() != ShopStatus.OPEN) {
            throw new BadRequestException("Selected shop is not open");
        }

        LeaseContract contract = tenant.requestRentStore(shop, appContext.service, appContext.leaseFactory, baseRent);
        appContext.contracts.add(contract);
        appContext.activeContractIdByUsername.put(username, contract.getContractId());
        appContext.logs.add(0, now() + " | Lease requested by " + username + " for shop " + shop.getShopNum());
        return contract;
    }

    private static void setActiveContract(String username, int contractId) {
        LeaseContract contract = findContractById(contractId);
        if (contract == null) {
            throw new NotFoundException("Contract not found");
        }
        if (!username.equals(findUsernameByUser(appContext, contract.getTenant()))) {
            throw new BadRequestException("Contract does not belong to current tenant");
        }
        appContext.activeContractIdByUsername.put(username, contractId);
        appContext.logs.add(0, now() + " | Active contract set for " + username + ": " + contractId);
    }

    private static void approveContract(int contractId) {
        LeaseContract contract = findContractById(contractId);
        if (contract == null) {
            throw new NotFoundException("Contract not found");
        }

        ContractManager manager = getFirstUserByRole(appContext, "contractmanager", ContractManager.class);
        Tenant tenant = contract.getTenant();
        if (manager == null || tenant == null) {
            throw new BadRequestException("Contract manager or tenant not available");
        }
        if (!manager.permitRequest(contract)) {
            throw new BadRequestException("Only pending contracts can be approved");
        }

        manager.createContract(contract, appContext.service);
        tenant.signContract(contract);
        appContext.logs.add(0, now() + " | Contract approved: " + contractId);
    }

    private static Payment uploadSales(String username, String month, double sales) {
        if (month.isBlank()) {
            throw new BadRequestException("Month is required");
        }
        if (sales < 0) {
            throw new BadRequestException("Sales must be non-negative");
        }

        User user = appContext.usersByUsername.get(username);
        if (!(user instanceof Tenant tenant)) {
            throw new BadRequestException("Tenant account is required");
        }

        Integer activeContractId = appContext.activeContractIdByUsername.get(username);
        if (activeContractId == null) {
            throw new BadRequestException("No active contract selected");
        }

        LeaseContract contract = findContractById(activeContractId);
        if (contract == null) {
            throw new NotFoundException("Active contract not found");
        }
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BadRequestException("Contract must be active before uploading sales");
        }
        if (contract.getTenant() != tenant) {
            throw new BadRequestException("Active contract does not belong to current tenant");
        }

        Payment payment = tenant.uploadMonthlyRecord(contract, month, sales, appContext.commissionStrategy,
                appContext.service);
        appContext.payments.add(payment);
        appContext.paymentToContractId.put(payment.getPaymentId(), contract.getContractId());
        appContext.paymentMonthById.put(payment.getPaymentId(), month);
        appContext.paymentSalesById.put(payment.getPaymentId(), sales);
        appContext.logs.add(0, now() + " | Monthly sales uploaded for contract " + contract.getContractId()
                + ", payment " + payment.getPaymentId() + " generated");
        return payment;
    }

    private static void confirmPayment(int paymentId) {
        Payment payment = findPaymentById(paymentId);
        if (payment == null) {
            throw new NotFoundException("Payment not found");
        }

        Accounting accounting = getFirstUserByRole(appContext, "accounting", Accounting.class);
        if (accounting == null) {
            throw new BadRequestException("No accounting user found");
        }

        boolean confirmed = accounting.confirmPaymentTransfer(payment);
        if (!confirmed) {
            throw new BadRequestException("Payment cannot be confirmed");
        }
        appContext.logs.add(0, now() + " | Payment confirmed: " + paymentId);
    }

    private static void createShop(Map<String, Object> body) {
        String shopNum = requiredString(body, "shopNum");
        double area = requiredDouble(body, "area");
        String typeInput = requiredString(body, "type");

        if (area <= 0) {
            throw new BadRequestException("Area must be greater than zero");
        }
        ShopType type = parseShopTypeInput(typeInput);
        if (type == null) {
            throw new BadRequestException("Invalid shop type");
        }

        int nextShopId = appContext.shops.stream().map(Shop::getShopId).max(Comparator.naturalOrder()).orElse(0) + 1;
        Shop shop = new Shop(nextShopId, shopNum, ShopStatus.OPEN, area, type);
        appContext.shops.add(shop);
        appContext.logs.add(0, now() + " | Shop created: " + shopNum);
    }

    private static void updateShop(int shopId, Map<String, Object> body) {
        Shop shop = findShopById(shopId);
        if (shop == null) {
            throw new NotFoundException("Shop not found");
        }

        String shopNum = requiredString(body, "shopNum");
        double area = requiredDouble(body, "area");
        String typeInput = requiredString(body, "type");
        String statusInput = requiredString(body, "status");

        if (area <= 0) {
            throw new BadRequestException("Area must be greater than zero");
        }

        ShopType type = parseShopTypeInput(typeInput);
        if (type == null) {
            throw new BadRequestException("Invalid shop type");
        }

        ShopStatus newStatus = parseShopStatus(statusInput);
        if (newStatus == null) {
            throw new BadRequestException("Invalid shop status");
        }

        ShopStatus previousStatus = shop.getStatus();
        shop.editStoreInfo(shopNum, area, type);
        shop.updateStoreStatus(newStatus);

        if (newStatus != previousStatus && (newStatus == ShopStatus.CLOSED || newStatus == ShopStatus.UNDER_REPAIR)) {
            Accounting accounting = getFirstUserByRole(appContext, "accounting", Accounting.class);
            if (accounting != null) {
                for (LeaseContract contract : appContext.contracts) {
                    if (contract.getShop().getShopId() == shopId) {
                        accounting.alterRentByStoreStatus(contract, shop);
                    }
                }
            }
        }

        appContext.logs.add(0, now() + " | Shop updated: " + shop.getShopNum() + " => " + shop.getStatus());
    }

    private static void deleteShop(int shopId) {
        Shop shop = findShopById(shopId);
        if (shop == null) {
            throw new NotFoundException("Shop not found");
        }

        for (LeaseContract contract : appContext.contracts) {
            if (contract.getShop().getShopId() == shopId) {
                throw new BadRequestException("Cannot delete shop with existing contracts");
            }
        }

        appContext.shops.remove(shop);
        appContext.logs.add(0, now() + " | Shop deleted: " + shop.getShopNum());
    }

    private static User createUser(String role, String username, String password) {
        if (!isCreatableRole(role)) {
            throw new BadRequestException("Unsupported role");
        }
        if (username.isBlank() || password.isBlank()) {
            throw new BadRequestException("Username and password are required");
        }
        if (appContext.usersByUsername.containsKey(username)) {
            throw new BadRequestException("Username already exists");
        }

        String userId = generateUserId(appContext);
        User user = appContext.systemAdmin.createAccount(role, userId, username, password);
        registerUser(appContext, user, role, password);
        appContext.logs.add(0, now() + " | User created: " + username + " (" + role + ")");
        return user;
    }

    private static void updatePassword(String username, String newPassword) {
        if (newPassword.isBlank()) {
            throw new BadRequestException("Password cannot be empty");
        }

        User user = appContext.usersByUsername.get(username);
        if (user == null) {
            throw new NotFoundException("User not found");
        }

        user.changePassword(newPassword);
        appContext.passwordByUsername.put(username, newPassword);
        appContext.logs.add(0, now() + " | Password updated for " + username);
    }

    private static void deleteUser(String username) {
        User user = appContext.usersByUsername.get(username);
        if (user == null) {
            throw new NotFoundException("User not found");
        }

        String role = appContext.roleByUsername.get(username);
        if ("systemadmin".equals(role)) {
            throw new BadRequestException("Cannot delete system admin account");
        }

        if (user instanceof Tenant tenant) {
            for (LeaseContract contract : appContext.contracts) {
                if (contract.getTenant() == tenant) {
                    throw new BadRequestException("Cannot delete tenant with existing contracts");
                }
            }
        }

        appContext.usersByUsername.remove(username);
        appContext.roleByUsername.remove(username);
        appContext.passwordByUsername.remove(username);
        appContext.activeContractIdByUsername.remove(username);
        appContext.logs.add(0, now() + " | User deleted: " + username);
    }

    private static void setPermission(String userId, String permission) {
        if (permission.isBlank()) {
            throw new BadRequestException("Permission cannot be empty");
        }
        appContext.systemAdmin.changeUserPermission(userId, permission);
        appContext.logs.add(0, now() + " | Permission updated for " + userId + ": " + permission);
    }

    private static Map<String, Object> buildStateResponse() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("users", usersDto());
        state.put("permissions", appContext.systemAdmin.getUserPermissionsSnapshot());
        state.put("shops", shopsDto());
        state.put("contracts", contractsDto());
        state.put("payments", paymentsDto());
        state.put("activeContractIdByUser", new HashMap<>(appContext.activeContractIdByUsername));

        List<String> logs = new ArrayList<>(appContext.logs);
        logs.addAll(appContext.auditLogListener.getMessages());
        state.put("logs", logs.stream().distinct().limit(120).toList());
        return state;
    }

    private static List<Map<String, Object>> usersDto() {
        List<Map<String, Object>> users = new ArrayList<>();
        for (Map.Entry<String, User> entry : appContext.usersByUsername.entrySet()) {
            String username = entry.getKey();
            User user = entry.getValue();
            String role = appContext.roleByUsername.getOrDefault(username, "unknown");
            users.add(userDto(user, role));
        }
        users.sort(Comparator.comparing(u -> String.valueOf(u.get("username"))));
        return users;
    }

    private static List<Map<String, Object>> shopsDto() {
        List<Map<String, Object>> shops = new ArrayList<>();
        for (Shop shop : appContext.shops) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("shopId", shop.getShopId());
            item.put("shopNum", shop.getShopNum());
            item.put("status", shop.getStatus().name());
            item.put("area", shop.getArea());
            item.put("type", shop.getType().name());
            shops.add(item);
        }
        shops.sort(Comparator.comparingInt(s -> (int) s.get("shopId")));
        return shops;
    }

    private static List<Map<String, Object>> contractsDto() {
        List<Map<String, Object>> contracts = new ArrayList<>();
        for (LeaseContract contract : appContext.contracts) {
            contracts.add(contractDto(contract));
        }
        contracts.sort(Comparator.comparingInt(c -> (int) c.get("contractId")));
        return contracts;
    }

    private static List<Map<String, Object>> paymentsDto() {
        List<Map<String, Object>> payments = new ArrayList<>();
        for (Payment payment : appContext.payments) {
            payments.add(paymentDto(payment));
        }
        payments.sort(Comparator.comparingInt(p -> (int) p.get("paymentId")));
        return payments;
    }

    private static Map<String, Object> userDto(User user, String role) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("userId", user.getUserId());
        dto.put("username", user.getUsername());
        dto.put("role", role);
        return dto;
    }

    private static Map<String, Object> contractDto(LeaseContract contract) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("contractId", contract.getContractId());
        dto.put("shopId", contract.getShop().getShopId());
        dto.put("tenantUsername", findUsernameByUser(appContext, contract.getTenant()));
        dto.put("status", contract.getStatus().name());
        dto.put("baseRent", contract.getBaseRent());
        dto.put("commissionRate", contract.getCommissionRate());
        return dto;
    }

    private static Map<String, Object> paymentDto(Payment payment) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("paymentId", payment.getPaymentId());
        dto.put("contractId", appContext.paymentToContractId.getOrDefault(payment.getPaymentId(), -1));
        dto.put("month", appContext.paymentMonthById.getOrDefault(payment.getPaymentId(), "N/A"));
        dto.put("sales", appContext.paymentSalesById.getOrDefault(payment.getPaymentId(), 0.0));
        dto.put("amount", payment.getAmount());
        dto.put("status", payment.getStatus().name());
        return dto;
    }

    private static LeaseContract findContractById(int contractId) {
        for (LeaseContract contract : appContext.contracts) {
            if (contract.getContractId() == contractId) {
                return contract;
            }
        }
        return null;
    }

    private static Payment findPaymentById(int paymentId) {
        for (Payment payment : appContext.payments) {
            if (payment.getPaymentId() == paymentId) {
                return payment;
            }
        }
        return null;
    }

    private static Shop findShopById(int shopId) {
        for (Shop shop : appContext.shops) {
            if (shop.getShopId() == shopId) {
                return shop;
            }
        }
        return null;
    }

    private static boolean isCreatableRole(String role) {
        return "tenant".equals(role) || "accounting".equals(role) || "contractmanager".equals(role)
                || "floor".equals(role);
    }

    private static String generateUserId(AppContext ctx) {
        String userId = "U" + ctx.nextUserNumber;
        ctx.nextUserNumber++;
        return userId;
    }

    private static void loadStateQuietly(AppContext ctx) {
        try {
            boolean loaded = loadState(ctx);
            if (loaded) {
                ctx.logs.add(0, now() + " | State loaded from " + STATE_FILE);
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
            ctx.shops.clear();
            for (int i = 0; i < shopCount; i++) {
                int shopId = getIntProperty(props, "shop." + i + ".id", i + 1);
                String shopNum = props.getProperty("shop." + i + ".num", "S-" + shopId);
                ShopStatus status;
                try {
                    status = ShopStatus.valueOf(props.getProperty("shop." + i + ".status", ShopStatus.OPEN.name()));
                } catch (IllegalArgumentException ex) {
                    status = ShopStatus.OPEN;
                }
                double area = getDoubleProperty(props, "shop." + i + ".area", 30.0);
                ShopType type = getShopTypeProperty(props, "shop." + i + ".type", ShopType.SOLID);
                ctx.shops.add(new Shop(shopId, shopNum, status, area, type));
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

            ContractStatus status;
            try {
                status = ContractStatus.valueOf(
                        props.getProperty("contract." + i + ".status", ContractStatus.DRAFT.name()));
            } catch (IllegalArgumentException ex) {
                status = ContractStatus.DRAFT;
            }

            Shop shop = findShopById(ctx.shops, shopId);
            User tenantUser = ctx.usersByUsername.get(tenantUsername);
            if (shop != null && tenantUser instanceof Tenant tenant) {
                LeaseContract contract = new LeaseContract(contractId, shop, tenant, baseRent, commissionRate);
                contract.restoreStatus(status);
                ctx.contracts.add(contract);
            }
        }

        ctx.payments.clear();
        ctx.paymentToContractId.clear();
        ctx.paymentMonthById.clear();
        ctx.paymentSalesById.clear();

        int paymentCount = getIntProperty(props, "payment.count", 0);
        for (int i = 0; i < paymentCount; i++) {
            int paymentId = getIntProperty(props, "payment." + i + ".id", i + 1);
            double amount = getDoubleProperty(props, "payment." + i + ".amount", 0);

            PaymentStatus status;
            try {
                status = PaymentStatus
                        .valueOf(props.getProperty("payment." + i + ".status", PaymentStatus.UNVERIFIED.name()));
            } catch (IllegalArgumentException ex) {
                status = PaymentStatus.UNVERIFIED;
            }

            int contractId = getIntProperty(props, "payment." + i + ".contractId", -1);
            String month = props.getProperty("payment." + i + ".month", "N/A");
            double sales = getDoubleProperty(props, "payment." + i + ".sales", 0.0);

            Payment payment = new Payment(paymentId, amount, status);
            ctx.payments.add(payment);
            if (contractId > 0) {
                ctx.paymentToContractId.put(paymentId, contractId);
            }
            ctx.paymentMonthById.put(paymentId, month);
            ctx.paymentSalesById.put(paymentId, sales);
        }

        ctx.activeContractIdByUsername.clear();
        int activeByUserCount = getIntProperty(props, "activeContract.count", 0);
        if (activeByUserCount > 0) {
            for (int i = 0; i < activeByUserCount; i++) {
                String username = props.getProperty("activeContract." + i + ".username", "");
                int contractId = getIntProperty(props, "activeContract." + i + ".contractId", -1);
                if (!username.isBlank() && contractId > 0 && ctx.usersByUsername.containsKey(username)) {
                    ctx.activeContractIdByUsername.put(username, contractId);
                }
            }
        } else {
            int legacyActiveContractId = getIntProperty(props, "active.contractId", -1);
            if (legacyActiveContractId > 0) {
                LeaseContract contract = findContractById(legacyActiveContractId);
                if (contract != null) {
                    String username = findUsernameByUser(ctx, contract.getTenant());
                    if (!"unknown".equals(username)) {
                        ctx.activeContractIdByUsername.put(username, legacyActiveContractId);
                    }
                }
            }
        }

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

        props.setProperty("shop.count", String.valueOf(ctx.shops.size()));
        for (int i = 0; i < ctx.shops.size(); i++) {
            Shop shop = ctx.shops.get(i);
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
            props.setProperty("payment." + i + ".month",
                    ctx.paymentMonthById.getOrDefault(payment.getPaymentId(), "N/A"));
            props.setProperty("payment." + i + ".sales",
                    String.valueOf(ctx.paymentSalesById.getOrDefault(payment.getPaymentId(), 0.0)));
        }

        props.setProperty("activeContract.count", String.valueOf(ctx.activeContractIdByUsername.size()));
        int activeIndex = 0;
        for (Map.Entry<String, Integer> entry : ctx.activeContractIdByUsername.entrySet()) {
            props.setProperty("activeContract." + activeIndex + ".username", entry.getKey());
            props.setProperty("activeContract." + activeIndex + ".contractId", String.valueOf(entry.getValue()));
            activeIndex++;
        }

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
            props.store(out, "Shop Rental V1 API State");
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
            case "tenant", "accounting", "contractmanager", "floor" ->
                ctx.systemAdmin.createAccount(role, userId, username, password);
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

    private static Shop findShopById(List<Shop> shops, int shopId) {
        for (Shop shop : shops) {
            if (shop.getShopId() == shopId) {
                return shop;
            }
        }
        return null;
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

    private static ShopType parseShopTypeInput(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toUpperCase();
        return switch (normalized) {
            case "FNB", "F&B" -> ShopType.FNB;
            case "POPUP", "POP-UP", "POP_UP" -> ShopType.POPUP;
            case "SOLID", "SOILD" -> ShopType.SOLID;
            default -> null;
        };
    }

    private static ShopStatus parseShopStatus(String input) {
        if (input == null) {
            return null;
        }
        try {
            return ShopStatus.valueOf(input.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int parseId(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid id");
        }
    }

    private static Map<String, String> readStringMapBody(HttpExchange exchange) throws IOException {
        Type mapType = new TypeToken<Map<String, String>>() {
        }.getType();
        Map<String, String> body = readJsonBody(exchange, mapType);
        return body == null ? Map.of() : body;
    }

    private static Map<String, Object> readObjectMapBody(HttpExchange exchange) throws IOException {
        Type mapType = new TypeToken<Map<String, Object>>() {
        }.getType();
        Map<String, Object> body = readJsonBody(exchange, mapType);
        return body == null ? Map.of() : body;
    }

    private static <T> T readJsonBody(HttpExchange exchange, Type type) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) {
                return null;
            }
            return GSON.fromJson(json, type);
        } catch (JsonSyntaxException ex) {
            throw new BadRequestException("Invalid JSON body");
        }
    }

    private static String required(Map<String, String> body, String key) {
        String value = body.get(key);
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Missing field: " + key);
        }
        return value;
    }

    private static String requiredString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            throw new BadRequestException("Missing field: " + key);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            throw new BadRequestException("Missing field: " + key);
        }
        return text;
    }

    private static int requiredInt(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            throw new BadRequestException("Missing field: " + key);
        }
        if (value instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid integer field: " + key);
        }
    }

    private static double requiredDouble(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            throw new BadRequestException("Missing field: " + key);
        }
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid number field: " + key);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx < 0) {
                query.put(decodePathSegment(pair), "");
            } else {
                String key = decodePathSegment(pair.substring(0, idx));
                String value = decodePathSegment(pair.substring(idx + 1));
                query.put(key, value);
            }
        }
        return query;
    }

    private static String decodePathSegment(String encoded) {
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String contentTypeFor(Path filePath) {
        String name = filePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (name.endsWith(".js")) {
            return "text/javascript; charset=UTF-8";
        }
        if (name.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    private static void sendText(HttpExchange exchange, int status, String body, String contentType)
            throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] data = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static final class AppContext {
        private final ShopRentalService service;
        private final SystemAdmin systemAdmin;
        private final List<Shop> shops;
        private final LeaseContractFactory leaseFactory;
        private final CommissionStrategy commissionStrategy;
        private final MemoryAuditLogListener auditLogListener;

        private final Map<String, User> usersByUsername = new HashMap<>();
        private final Map<String, String> roleByUsername = new HashMap<>();
        private final Map<String, String> passwordByUsername = new HashMap<>();

        private final List<LeaseContract> contracts = new ArrayList<>();
        private final List<Payment> payments = new ArrayList<>();

        private final Map<Integer, Integer> paymentToContractId = new HashMap<>();
        private final Map<Integer, String> paymentMonthById = new HashMap<>();
        private final Map<Integer, Double> paymentSalesById = new HashMap<>();
        private final Map<String, Integer> activeContractIdByUsername = new HashMap<>();

        private final List<String> logs = new ArrayList<>();
        private int nextUserNumber = 1000;

        private AppContext(ShopRentalService service,
                SystemAdmin systemAdmin,
                List<Shop> shops,
                LeaseContractFactory leaseFactory,
                CommissionStrategy commissionStrategy,
                MemoryAuditLogListener auditLogListener) {
            this.service = service;
            this.systemAdmin = systemAdmin;
            this.shops = shops;
            this.leaseFactory = leaseFactory;
            this.commissionStrategy = commissionStrategy;
            this.auditLogListener = auditLogListener;
        }
    }

    private static final class MemoryAuditLogListener implements RentalEventListener {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void onEvent(RentalEvent event) {
            messages.add(0, event.getTimestamp() + " | " + event.getType() + " | " + event.getMessage());
            if (messages.size() > 120) {
                messages.remove(messages.size() - 1);
            }
        }

        private List<String> getMessages() {
            return new ArrayList<>(messages);
        }
    }

    private static final class BadRequestException extends RuntimeException {
        private BadRequestException(String message) {
            super(message);
        }
    }

    private static final class UnauthorizedException extends RuntimeException {
        private UnauthorizedException(String message) {
            super(message);
        }
    }

    private static final class NotFoundException extends RuntimeException {
        private NotFoundException(String message) {
            super(message);
        }
    }
}
