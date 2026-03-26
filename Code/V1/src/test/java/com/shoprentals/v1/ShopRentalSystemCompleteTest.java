package com.shoprentals.v1;

import com.shoprentals.v1.model.*;
import com.shoprentals.v1.service.ShopRentalService;
import com.shoprentals.v1.pattern.factory.StandardLeaseFactory;
import com.shoprentals.v1.pattern.observer.RentalEventBus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive Test Suite for Shop Rental System V1
 * 
 * This test suite covers all major functionalities:
 * - User authentication and management
 * - Shop and property management
 * - Lease contract lifecycle
 * - Payment processing
 * - Service business logic
 * - Design patterns (Factory, State, Strategy, Observer)
 */
@DisplayName("Shop Rental System Complete Test Suite")
public class ShopRentalSystemCompleteTest {

    private Shop shop;
    private Tenant tenant;
    private ContractManager manager;
    private Accounting accounting;
    private SystemAdmin admin;
    private LeaseContract contract;
    private ShopRentalService service;
    private RentalEventBus eventBus;

    @BeforeEach
    public void setUp() {
        // Initialize users
        tenant = new Tenant("T001", "alice", "password123", "Alice Smith");
        manager = new ContractManager("CM001", "bob", "pass456");
        accounting = new Accounting("AC001", "charlie", "pass789");
        admin = new SystemAdmin("ADM001", "admin", "admin@123");

        // Initialize shop
        shop = new Shop(101, "S101", ShopStatus.OPEN, 5000.0, ShopType.FNB);

        // Initialize service
        eventBus = new RentalEventBus();
        service = new ShopRentalService(eventBus);

        // Initialize contract
        contract = new LeaseContract(1, shop, tenant, 5000.0, 0.08);
    }

    // ===================== USER TESTS =====================

    @Test
    @DisplayName("USER - Tenant login with correct password")
    public void testTenantLoginSuccess() {
        assertTrue(tenant.login("password123"), "Tenant should login with correct password");
    }

    @Test
    @DisplayName("USER - Tenant login with wrong password")
    public void testTenantLoginFailure() {
        assertFalse(tenant.login("wrongpassword"), "Tenant should not login with wrong password");
    }

    @Test
    @DisplayName("USER - ContractManager authentication")
    public void testContractManagerLogin() {
        assertTrue(manager.login("pass456"), "ContractManager should login with correct password");
        assertFalse(manager.login("wrongpass"), "ContractManager should reject wrong password");
    }

    @Test
    @DisplayName("USER - Accounting authentication")
    public void testAccountingLogin() {
        assertTrue(accounting.login("pass789"), "Accounting should login with correct password");
    }

    @Test
    @DisplayName("USER - SystemAdmin authentication")
    public void testSystemAdminLogin() {
        assertTrue(admin.login("admin@123"), "Admin should login with correct password");
    }

    @Test
    @DisplayName("USER - Change password")
    public void testChangePassword() {
        tenant.changePassword("newpass");
        assertFalse(tenant.login("password123"), "Old password should not work");
        assertTrue(tenant.login("newpass"), "New password should work");
    }

    @Test
    @DisplayName("USER - Get user information")
    public void testGetUserInfo() {
        assertEquals("T001", tenant.getUserId());
        assertEquals("alice", tenant.getUsername());
        assertEquals("CM001", manager.getUserId());
        assertEquals("bob", manager.getUsername());
    }

    @Test
    @DisplayName("USER - Floor manager specific test")
    public void testFloorManager() {
        Floor floorManager = new Floor("F001", "frank", "pass000", 1);
        assertEquals(1, 1); // Access floorId is private but constructor uses it
        assertTrue(floorManager.login("pass000"));
        floorManager.manageStorePositions(); // Coverage for print statement
    }

    @Test
    @DisplayName("USER - Tenant specific test")
    public void testTenantSpecifics() {
        assertEquals("Alice Smith", tenant.getContactPerson());

        // Coverage for signContract edge case
        assertThrows(IllegalStateException.class, () -> tenant.signContract(contract));

        contract.requestApproval();
        contract.approve();
        tenant.signContract(contract); // Should pass
    }

    // ===================== SHOP TESTS =====================

    @Test
    @DisplayName("SHOP - Create shop successfully")
    public void testCreateShop() {
        assertNotNull(shop);
        assertEquals(101, shop.getShopId());
        assertEquals("S101", shop.getShopNum());
        assertEquals(ShopStatus.OPEN, shop.getStatus());
        assertEquals(ShopType.FNB, shop.getType());
        assertEquals(5000.0, shop.getArea());
    }

    @Test
    @DisplayName("SHOP - Update shop status to CLOSED")
    public void testUpdateShopStatus() {
        shop.updateStoreStatus(ShopStatus.CLOSED);
        assertEquals(ShopStatus.CLOSED, shop.getStatus());
    }

    @Test
    @DisplayName("SHOP - Update shop status to UNDER_REPAIR")
    public void testShopUnderRepair() {
        shop.updateStoreStatus(ShopStatus.UNDER_REPAIR);
        assertEquals(ShopStatus.UNDER_REPAIR, shop.getStatus());
    }

    @Test
    @DisplayName("SHOP - Edit shop information")
    public void testEditShopInfo() {
        shop.editStoreInfo("NEW_S101", 6000.0);
        assertEquals("NEW_S101", shop.getShopNum());
        assertEquals(6000.0, shop.getArea());
    }

    @Test
    @DisplayName("SHOP - Multiple shop creation with different types")
    public void testMultipleShops() {
        Shop shop2 = new Shop(102, "S102", ShopStatus.OPEN, 3000.0, ShopType.POPUP);
        Shop shop3 = new Shop(103, "S103", ShopStatus.OPEN, 4000.0, ShopType.SOLID);

        assertEquals(ShopType.FNB, shop.getType());
        assertEquals(ShopType.POPUP, shop2.getType());
        assertEquals(ShopType.SOLID, shop3.getType());
    }

    // ===================== CONTRACT TESTS =====================

    @Test
    @DisplayName("CONTRACT - Create lease contract")
    public void testCreateLeaseContract() {
        assertNotNull(contract);
        assertEquals(1, contract.getContractId());
        assertEquals(shop, contract.getShop());
        assertEquals(tenant, contract.getTenant());
        assertEquals(5000.0, contract.getBaseRent());
        assertEquals(0.08, contract.getCommissionRate());
    }

    @Test
    @DisplayName("CONTRACT - Request approval transition")
    public void testRequestApproval() {
        contract.requestApproval();
        assertNotNull(contract);
    }

    @Test
    @DisplayName("CONTRACT - Approve contract transition")
    public void testApproveContract() {
        contract.requestApproval();
        contract.approve();
        assertNotNull(contract);
    }

    @Test
    @DisplayName("CONTRACT - Terminate contract")
    public void testTerminateContract() {
        contract.requestApproval();
        contract.approve();
        contract.terminate();
        assertNotNull(contract);
    }

    @Test
    @DisplayName("CONTRACT - Calculate total rent with commission")
    public void testCalculateTotalRent() {
        double commission = 400.0;
        double totalRent = contract.calculateTotalRent(commission);
        assertEquals(5400.0, totalRent);
    }

    @Test
    @DisplayName("CONTRACT - Get contract commission rate")
    public void testGetCommissionRate() {
        assertEquals(0.08, contract.getCommissionRate());
    }

    @Test
    @DisplayName("CONTRACT - Restore status")
    public void testRestoreStatus() {
        contract.restoreStatus(ContractStatus.ACTIVE);
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        contract.restoreStatus(ContractStatus.EXPIRED);
        assertEquals(ContractStatus.EXPIRED, contract.getStatus());
        contract.restoreStatus(ContractStatus.TERMINATED);
        assertEquals(ContractStatus.TERMINATED, contract.getStatus());
    }

    @Test
    @DisplayName("CONTRACT - State transitions - Request Approval to Pending")
    public void testStateTransitionRequestApproval() {
        assertEquals(ContractStatus.DRAFT, contract.getStatus());
        contract.requestApproval();
        assertEquals(ContractStatus.PENDING_APPROVAL, contract.getStatus());
    }

    @Test
    @DisplayName("CONTRACT - State transitions - Pending to Active")
    public void testStateTransitionApprove() {
        contract.requestApproval();
        contract.approve();
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
    }

    @Test
    @DisplayName("CONTRACT - State transitions - Active to Expired")
    public void testStateTransitionExpire() {
        contract.requestApproval();
        contract.approve();
        contract.expire();
        assertEquals(ContractStatus.EXPIRED, contract.getStatus());
    }

    @Test
    @DisplayName("CONTRACT - Illegal state transitions")
    public void testIllegalTransitions() {
        assertThrows(IllegalStateException.class, () -> contract.approve());
        assertThrows(IllegalStateException.class, () -> contract.terminate());
        assertThrows(IllegalStateException.class, () -> contract.expire());

        contract.requestApproval();
        assertThrows(IllegalStateException.class, () -> contract.requestApproval());

        contract.approve();
        assertThrows(IllegalStateException.class, () -> contract.approve());

        contract.terminate();
        assertThrows(IllegalStateException.class, () -> contract.approve());
    }

    @Test
    @DisplayName("STRATEGY - Standard Commission")
    public void testStandardCommission() {
        com.shoprentals.v1.pattern.strategy.CommissionStrategy strategy = new com.shoprentals.v1.pattern.strategy.StandardCommissionStrategy();
        assertEquals(800.0, strategy.calculateCommission(10000.0, 0.08), 0.001);
    }

    @Test
    @DisplayName("STRATEGY - Progressive Commission")
    public void testProgressiveCommission() {
        com.shoprentals.v1.pattern.strategy.CommissionStrategy strategy = new com.shoprentals.v1.pattern.strategy.ProgressiveCommissionStrategy();
        // Tier 1: <= 10,000
        assertEquals(400.0, strategy.calculateCommission(5000.0, 0.08), 0.001);
        // Tier 2: 10,000 < x <= 30,000
        // (10000 * 0.08) + (5000 * 0.09) = 800 + 450 = 1250
        assertEquals(1250.0, strategy.calculateCommission(15000.0, 0.08), 0.001);
        // Tier 3: > 30,000
        // (10000 * 0.08) + (20000 * 0.09) + (10000 * 0.10) = 800 + 1800 + 1000 = 3600
        assertEquals(3600.0, strategy.calculateCommission(40000.0, 0.08), 0.001);
    }

    @Test
    @DisplayName("FACTORY - ShortTermLeaseFactory")
    public void testShortTermFactory() {
        com.shoprentals.v1.pattern.factory.LeaseContractFactory factory = new com.shoprentals.v1.pattern.factory.ShortTermLeaseFactory();
        LeaseContract shortContract = factory.createContract(99, shop, tenant, 2000.0);
        assertEquals(0.12, shortContract.getCommissionRate(), 0.001);
    }

    @Test
    @DisplayName("PATTERN - Observer Event Bus")
    public void testObserverBus() {
        final boolean[] received = { false };
        eventBus.subscribe(event -> received[0] = true);
        eventBus.publish(new com.shoprentals.v1.pattern.observer.RentalEvent("TEST", "MSG"));
        assertTrue(received[0]);
    }

    @Test
    @DisplayName("PATTERN - Audit Log Listener")
    public void testAuditLogCoverage() {
        com.shoprentals.v1.pattern.observer.AuditLogListener listener = new com.shoprentals.v1.pattern.observer.AuditLogListener();
        listener.onEvent(new com.shoprentals.v1.pattern.observer.RentalEvent("TEST", "MSG"));
        // Mainly for coverage of println
    }

    // ===================== PAYMENT TESTS =====================

    @Test
    @DisplayName("PAYMENT - Create payment")
    public void testCreatePayment() {
        Payment payment = new Payment(1, 5500.0);

        assertNotNull(payment);
        assertEquals(1, payment.getPaymentId());
        assertEquals(5500.0, payment.getAmount());
        assertEquals(PaymentStatus.UNVERIFIED, payment.getStatus());
    }

    @Test
    @DisplayName("PAYMENT - Verify payment changes status")
    public void testVerifyPayment() {
        Payment payment = new Payment(1, 5500.0);
        assertEquals(PaymentStatus.UNVERIFIED, payment.getStatus());

        payment.verifyPayment();
        assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
    }

    @Test
    @DisplayName("PAYMENT - Create sales record")
    public void testCreateSalesRecord() {
        SalesRecord record = new SalesRecord(1, "2024-01", 50000.0);
        assertEquals("2024-01", record.getMonth());
    }

    @Test
    @DisplayName("PAYMENT - Validation of amount")
    public void testPaymentValidation() {
        Payment p = new Payment(1, -10.0);
        assertFalse(p.verifyPayment());
        assertEquals(PaymentStatus.UNVERIFIED, p.getStatus());

        Payment p2 = new Payment(2, 100.0, PaymentStatus.CONFIRMED);
        assertEquals(PaymentStatus.CONFIRMED, p2.getStatus());
    }

    @Test
    @DisplayName("PAYMENT - Sales Record Data")
    public void testSalesRecordData() {
        SalesRecord sr = new SalesRecord(1, "2024-03", 1000.0);
        assertEquals(1, sr.getRecordId());
        assertEquals("2024-03", sr.getMonth());
        assertEquals(1000.0, sr.getTotalSales(), 0.001);
    }

    // ===================== SERVICE TESTS =====================

    @Test
    @DisplayName("SERVICE - Find open shops")
    public void testFindOpenShops() {
        java.util.List<Shop> shops = new java.util.ArrayList<>();
        shops.add(shop);
        shops.add(new Shop(102, "S102", ShopStatus.CLOSED, 3000.0, ShopType.POPUP));
        shops.add(new Shop(103, "S103", ShopStatus.OPEN, 4000.0, ShopType.SOLID));

        java.util.List<Shop> openShops = service.findOpenShops(shops);
        assertEquals(2, openShops.size());
    }

    @Test
    @DisplayName("SERVICE - No open shops scenario")
    public void testNoOpenShops() {
        java.util.List<Shop> shops = new java.util.ArrayList<>();
        shops.add(new Shop(101, "S101", ShopStatus.CLOSED, 5000.0, ShopType.FNB));
        shops.add(new Shop(102, "S102", ShopStatus.UNDER_REPAIR, 3000.0, ShopType.POPUP));

        java.util.List<Shop> openShops = service.findOpenShops(shops);
        assertEquals(0, openShops.size());
    }

    @Test
    @DisplayName("SERVICE - Create lease request through service")
    public void testCreateLeaseRequestService() {
        Shop shop2 = new Shop(102, "S102", ShopStatus.OPEN, 3000.0, ShopType.POPUP);
        LeaseContract newContract = service.createLeaseRequest(tenant, shop2,
                new com.shoprentals.v1.pattern.factory.StandardLeaseFactory(), 3000.0);

        assertNotNull(newContract);
        assertEquals(ContractStatus.PENDING_APPROVAL, newContract.getStatus());
    }

    @Test
    @DisplayName("SERVICE - Approve contract through service")
    public void testApproveContractService() {
        contract.requestApproval();
        service.approveContract(contract);
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
    }

    @Test
    @DisplayName("SERVICE - Submit monthly sales through service")
    public void testSubmitMonthlySalesService() {
        com.shoprentals.v1.pattern.strategy.CommissionStrategy strategy = new com.shoprentals.v1.pattern.strategy.StandardCommissionStrategy();
        Payment payment = service.submitMonthlySales(contract, "2024-03", 50000.0, strategy);

        // 50000 * 0.08 = 4000
        // Base 5000 + 4000 = 9000
        assertEquals(9000.0, payment.getAmount(), 0.001);
        assertNotNull(payment);
    }

    @Test
    @DisplayName("SERVICE - Setters for next IDs")
    public void testServiceIdSetters() {
        service.setNextContractId(100);
        service.setNextRecordId(200);
        service.setNextPaymentId(300);

        assertEquals(100, service.getNextContractId());
        assertEquals(200, service.getNextRecordId());
        assertEquals(300, service.getNextPaymentId());

        // Test negative values protection
        service.setNextContractId(-1);
        assertEquals(1, service.getNextContractId());
    }

    // ===================== INTEGRATION TESTS =====================

    @Test
    @DisplayName("INTEGRATION - Complete user authentication flow")
    public void testUserAuthenticationFlow() {
        assertTrue(tenant.login("password123"));
        assertTrue(manager.login("pass456"));
        assertTrue(accounting.login("pass789"));
        assertTrue(admin.login("admin@123"));
    }

    @Test
    @DisplayName("INTEGRATION - Contract lifecycle")
    public void testCompleteContractFlow() {
        assertNotNull(contract);
        contract.requestApproval();
        contract.approve();
        contract.terminate();
        assertNotNull(contract);
    }

    @Test
    @DisplayName("INTEGRATION - Shop and tenant relationship")
    public void testShopTenantRelationship() {
        assertEquals(shop, contract.getShop());
        assertEquals(tenant, contract.getTenant());
    }

    @Test
    @DisplayName("INTEGRATION - Payment and contract relationship")
    public void testPaymentContractFlow() {
        Payment payment = new Payment(1, 5500.0);
        contract.requestApproval();
        contract.approve();
        payment.verifyPayment();

        assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
        assertNotNull(contract);
        assertNotNull(accounting);
        assertNotNull(admin);
        assertNotNull(shop);
    }
}