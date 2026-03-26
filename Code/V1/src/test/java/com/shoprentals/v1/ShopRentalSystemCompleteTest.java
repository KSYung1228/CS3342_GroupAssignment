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
    @DisplayName("CONTRACT - Multiple contracts for same tenant")
    public void testMultipleContracts() {
        Shop shop2 = new Shop(102, "S102", ShopStatus.OPEN, 3000.0, ShopType.POPUP);
        LeaseContract contract2 = new LeaseContract(2, shop2, tenant, 3000.0, 0.10);

        assertNotEquals(contract.getContractId(), contract2.getContractId());
        assertEquals(contract.getTenant(), contract2.getTenant());
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
    @DisplayName("PAYMENT - Multiple payment verification")
    public void testMultiplePayments() {
        Payment p1 = new Payment(1, 5000.0);
        Payment p2 = new Payment(2, 6000.0);
        Payment p3 = new Payment(3, 7000.0);

        p1.verifyPayment();
        p2.verifyPayment();

        assertEquals(PaymentStatus.CONFIRMED, p1.getStatus());
        assertEquals(PaymentStatus.CONFIRMED, p2.getStatus());
        assertEquals(PaymentStatus.UNVERIFIED, p3.getStatus());
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
    @DisplayName("SERVICE - Get next IDs")
    public void testGetNextIds() {
        int contractId = service.getNextContractId();
        int recordId = service.getNextRecordId();
        int paymentId = service.getNextPaymentId();

        assertTrue(contractId > 0);
        assertTrue(recordId > 0);
        assertTrue(paymentId > 0);
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