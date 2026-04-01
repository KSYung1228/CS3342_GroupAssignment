package com.shoprentals.v2;

import com.shoprentals.v2.model.*;
import com.shoprentals.v2.pattern.factory.*;
import com.shoprentals.v2.pattern.observer.*;
import com.shoprentals.v2.pattern.strategy.*;
import com.shoprentals.v2.service.ShopRentalService;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

/**
 * V2 Test Suite - covers all major functionalities and design patterns.
 */
@DisplayName("Shop Rental System V2 Test Suite")
public class ShopRentalV2Test {

    private Shop shop;
    private Tenant tenant;
    private ContractManager manager;
    private Accounting accounting;
    private SystemAdmin admin;
    private LeaseContract contract;
    private ShopRentalService service;
    private RentalEventBus eventBus;

    @BeforeEach
    void setUp() {
        tenant    = new Tenant("T001", "alice", "pass123", "Alice");
        manager   = new ContractManager("CM001", "bob", "pass456");
        accounting = new Accounting("AC001", "charlie", "pass789");
        admin     = new SystemAdmin("ADM001", "admin", "admin123");
        shop      = new Shop(1, "A-101", ShopStatus.OPEN, 35.0, ShopType.FNB, 1, 1, 2, 2);
        eventBus  = new RentalEventBus();
        service   = new ShopRentalService(eventBus);
        contract  = new LeaseContract(1, shop, tenant, 5000.0, 0.08);
    }

    // ── User Tests ────────────────────────────────────────────────────────────

    @Test @DisplayName("USER - Tenant login success")
    void testTenantLoginSuccess() { assertTrue(tenant.login("pass123")); }

    @Test @DisplayName("USER - Tenant login failure")
    void testTenantLoginFailure() { assertFalse(tenant.login("wrong")); }

    @Test @DisplayName("USER - ContractManager login")
    void testManagerLogin() { assertTrue(manager.login("pass456")); assertFalse(manager.login("bad")); }

    @Test @DisplayName("USER - Accounting login")
    void testAccountingLogin() { assertTrue(accounting.login("pass789")); }

    @Test @DisplayName("USER - SystemAdmin login")
    void testAdminLogin() { assertTrue(admin.login("admin123")); }

    @Test @DisplayName("USER - Change password")
    void testChangePassword() {
        tenant.changePassword("newpass");
        assertFalse(tenant.login("pass123"));
        assertTrue(tenant.login("newpass"));
    }

    @Test @DisplayName("USER - Get user info")
    void testGetUserInfo() {
        assertEquals("T001", tenant.getUserId());
        assertEquals("alice", tenant.getUsername());
    }

    @Test @DisplayName("USER - SystemAdmin create accounts")
    void testAdminCreateAccounts() {
        User t = admin.createAccount("tenant", "U1", "newTenant", "pw");
        User cm = admin.createAccount("contractmanager", "U2", "newCM", "pw");
        User ac = admin.createAccount("accounting", "U3", "newAcct", "pw");
        assertInstanceOf(Tenant.class, t);
        assertInstanceOf(ContractManager.class, cm);
        assertInstanceOf(Accounting.class, ac);
    }

    // ── Shop Tests ────────────────────────────────────────────────────────────

    @Test @DisplayName("SHOP - Creation with floor plan position")
    void testShopCreation() {
        assertEquals(1, shop.getShopId());
        assertEquals("A-101", shop.getShopNum());
        assertEquals(ShopStatus.OPEN, shop.getStatus());
        assertEquals(35.0, shop.getArea());
        assertEquals(ShopType.FNB, shop.getType());
        assertEquals(1, shop.getPosX());
        assertEquals(1, shop.getPosY());
        assertEquals(2, shop.getWidth());
        assertEquals(2, shop.getHeight());
    }

    @Test @DisplayName("SHOP - Update status")
    void testShopStatusUpdate() {
        shop.updateStoreStatus(ShopStatus.CLOSED);
        assertEquals(ShopStatus.CLOSED, shop.getStatus());
        shop.updateStoreStatus(ShopStatus.UNDER_REPAIR);
        assertEquals(ShopStatus.UNDER_REPAIR, shop.getStatus());
    }

    @Test @DisplayName("SHOP - Edit info")
    void testShopEditInfo() {
        shop.editStoreInfo("B-201", 50.0, ShopType.POPUP);
        assertEquals("B-201", shop.getShopNum());
        assertEquals(50.0, shop.getArea());
        assertEquals(ShopType.POPUP, shop.getType());
    }

    @Test @DisplayName("SHOP - Set position")
    void testShopSetPosition() {
        shop.setPosition(3, 2, 3, 3);
        assertEquals(3, shop.getPosX());
        assertEquals(2, shop.getPosY());
        assertEquals(3, shop.getWidth());
        assertEquals(3, shop.getHeight());
    }

    // ── Contract State Tests ──────────────────────────────────────────────────

    @Test @DisplayName("CONTRACT - Initial state is DRAFT")
    void testInitialStateDraft() {
        assertEquals(ContractStatus.DRAFT, contract.getStatus());
    }

    @Test @DisplayName("CONTRACT - DRAFT -> PENDING_APPROVAL")
    void testRequestApproval() {
        contract.requestApproval();
        assertEquals(ContractStatus.PENDING_APPROVAL, contract.getStatus());
    }

    @Test @DisplayName("CONTRACT - PENDING_APPROVAL -> ACTIVE")
    void testApprove() {
        contract.requestApproval();
        contract.approve();
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
    }

    @Test @DisplayName("CONTRACT - ACTIVE -> TERMINATED")
    void testTerminate() {
        contract.requestApproval();
        contract.approve();
        contract.terminate();
        assertEquals(ContractStatus.TERMINATED, contract.getStatus());
    }

    @Test @DisplayName("CONTRACT - ACTIVE -> EXPIRED")
    void testExpire() {
        contract.requestApproval();
        contract.approve();
        contract.expire();
        assertEquals(ContractStatus.EXPIRED, contract.getStatus());
    }

    @Test @DisplayName("CONTRACT - PENDING_APPROVAL -> TERMINATED")
    void testTerminateFromPending() {
        contract.requestApproval();
        contract.terminate();
        assertEquals(ContractStatus.TERMINATED, contract.getStatus());
    }

    @Test @DisplayName("CONTRACT - Illegal transition throws exception")
    void testIllegalTransition() {
        assertThrows(IllegalStateException.class, () -> contract.approve());
    }

    @Test @DisplayName("CONTRACT - Calculate total rent")
    void testCalculateTotalRent() {
        double total = contract.calculateTotalRent(400.0);
        assertEquals(5400.0, total, 0.001);
    }

    @Test @DisplayName("CONTRACT - Restore status")
    void testRestoreStatus() {
        contract.restoreStatus(ContractStatus.ACTIVE);
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
    }

    // ── Strategy Tests ────────────────────────────────────────────────────────

    @Test @DisplayName("STRATEGY - Standard commission")
    void testStandardCommission() {
        CommissionStrategy s = new StandardCommissionStrategy();
        assertEquals(800.0, s.calculateCommission(10000.0, 0.08), 0.001);
    }

    @Test @DisplayName("STRATEGY - Progressive commission tier 1")
    void testProgressiveTier1() {
        CommissionStrategy s = new ProgressiveCommissionStrategy();
        assertEquals(800.0, s.calculateCommission(10000.0, 0.08), 0.001);
    }

    @Test @DisplayName("STRATEGY - Progressive commission tier 2")
    void testProgressiveTier2() {
        CommissionStrategy s = new ProgressiveCommissionStrategy();
        // 10000*0.08 + 10000*0.09 = 800 + 900 = 1700
        assertEquals(1700.0, s.calculateCommission(20000.0, 0.08), 0.001);
    }

    @Test @DisplayName("STRATEGY - Progressive commission tier 3")
    void testProgressiveTier3() {
        CommissionStrategy s = new ProgressiveCommissionStrategy();
        // 10000*0.08 + 20000*0.09 + 10000*0.10 = 800 + 1800 + 1000 = 3600
        assertEquals(3600.0, s.calculateCommission(40000.0, 0.08), 0.001);
    }

    // ── Factory Tests ─────────────────────────────────────────────────────────

    @Test @DisplayName("FACTORY - Standard lease 8% commission")
    void testStandardFactory() {
        LeaseContractFactory f = new StandardLeaseFactory();
        LeaseContract c = f.createContract(1, shop, tenant, 5000.0);
        assertEquals(0.08, c.getCommissionRate(), 0.001);
        assertEquals(5000.0, c.getBaseRent(), 0.001);
    }

    @Test @DisplayName("FACTORY - Short term lease 12% commission, 90% rent")
    void testShortTermFactory() {
        LeaseContractFactory f = new ShortTermLeaseFactory();
        LeaseContract c = f.createContract(1, shop, tenant, 5000.0);
        assertEquals(0.12, c.getCommissionRate(), 0.001);
        assertEquals(4500.0, c.getBaseRent(), 0.001);
    }

    // ── Observer Tests ────────────────────────────────────────────────────────

    @Test @DisplayName("OBSERVER - Event published on lease request")
    void testObserverOnLeaseRequest() {
        List<String> received = new java.util.ArrayList<>();
        eventBus.subscribe(event -> received.add(event.getType()));
        service.createLeaseRequest(tenant, shop, new StandardLeaseFactory(), 5000.0);
        assertTrue(received.contains("LEASE_REQUESTED"));
    }

    @Test @DisplayName("OBSERVER - Event published on contract approval")
    void testObserverOnApproval() {
        List<String> received = new java.util.ArrayList<>();
        eventBus.subscribe(event -> received.add(event.getType()));
        contract.requestApproval();
        service.approveContract(contract);
        assertTrue(received.contains("CONTRACT_APPROVED"));
    }

    // ── Service Tests ─────────────────────────────────────────────────────────

    @Test @DisplayName("SERVICE - Find open shops")
    void testFindOpenShops() {
        java.util.List<Shop> shops = new java.util.ArrayList<>();
        shops.add(new Shop(1, "A-101", ShopStatus.OPEN, 35.0, ShopType.FNB));
        shops.add(new Shop(2, "A-102", ShopStatus.CLOSED, 40.0, ShopType.POPUP));
        shops.add(new Shop(3, "B-101", ShopStatus.OPEN, 50.0, ShopType.SOLID));
        java.util.List<Shop> open = service.findOpenShops(shops);
        assertEquals(2, open.size());
    }

    @Test @DisplayName("SERVICE - Create lease request sets PENDING_APPROVAL")
    void testCreateLeaseRequest() {
        LeaseContract c = service.createLeaseRequest(tenant, shop, new StandardLeaseFactory(), 5000.0);
        assertEquals(ContractStatus.PENDING_APPROVAL, c.getStatus());
        assertEquals(tenant, c.getTenant());
        assertEquals(shop, c.getShop());
    }

    @Test @DisplayName("SERVICE - Approve contract sets ACTIVE")
    void testApproveContract() {
        LeaseContract c = service.createLeaseRequest(tenant, shop, new StandardLeaseFactory(), 5000.0);
        service.approveContract(c);
        assertEquals(ContractStatus.ACTIVE, c.getStatus());
    }

    @Test @DisplayName("SERVICE - Submit monthly sales generates payment")
    void testSubmitMonthlySales() {
        LeaseContract c = service.createLeaseRequest(tenant, shop, new StandardLeaseFactory(), 5000.0);
        service.approveContract(c);
        Payment p = service.submitMonthlySales(c, "2026-03", 20000.0, new ProgressiveCommissionStrategy());
        // commission: 10000*0.08 + 10000*0.09 = 1700; total = 5000 + 1700 = 6700
        assertEquals(6700.0, p.getAmount(), 0.001);
        assertEquals(PaymentStatus.UNVERIFIED, p.getStatus());
    }

    // ── Payment Tests ─────────────────────────────────────────────────────────

    @Test @DisplayName("PAYMENT - Verify payment")
    void testVerifyPayment() {
        Payment p = new Payment(1, 5000.0);
        assertEquals(PaymentStatus.UNVERIFIED, p.getStatus());
        assertTrue(p.verifyPayment());
        assertEquals(PaymentStatus.CONFIRMED, p.getStatus());
    }

    @Test @DisplayName("PAYMENT - Zero amount cannot be confirmed")
    void testZeroPaymentNotConfirmed() {
        Payment p = new Payment(1, 0.0);
        assertFalse(p.verifyPayment());
        assertEquals(PaymentStatus.UNVERIFIED, p.getStatus());
    }

    @Test @DisplayName("PAYMENT - Accounting confirms payment")
    void testAccountingConfirm() {
        Payment p = new Payment(1, 5000.0);
        assertTrue(accounting.confirmPaymentTransfer(p));
        assertEquals(PaymentStatus.CONFIRMED, p.getStatus());
    }

    // ── Accounting Business Logic ─────────────────────────────────────────────

    @Test @DisplayName("ACCOUNTING - Rent reduced 20% for UNDER_REPAIR")
    void testRentReductionUnderRepair() {
        contract.requestApproval();
        contract.approve();
        shop.updateStoreStatus(ShopStatus.UNDER_REPAIR);
        accounting.alterRentByStoreStatus(contract, shop);
        assertEquals(4000.0, contract.getBaseRent(), 0.001);
    }

    @Test @DisplayName("ACCOUNTING - Rent reduced 10% for CLOSED")
    void testRentReductionClosed() {
        contract.requestApproval();
        contract.approve();
        shop.updateStoreStatus(ShopStatus.CLOSED);
        accounting.alterRentByStoreStatus(contract, shop);
        assertEquals(4500.0, contract.getBaseRent(), 0.001);
    }

    // ── ContractManager Tests ─────────────────────────────────────────────────

    @Test @DisplayName("MANAGER - Permit request only for PENDING_APPROVAL")
    void testPermitRequest() {
        assertFalse(manager.permitRequest(contract)); // DRAFT
        contract.requestApproval();
        assertTrue(manager.permitRequest(contract));  // PENDING_APPROVAL
        contract.approve();
        assertFalse(manager.permitRequest(contract)); // ACTIVE
    }

    // ── Integration Test ──────────────────────────────────────────────────────

    @Test @DisplayName("INTEGRATION - Full lease workflow")
    void testFullLeaseWorkflow() {
        // 1. Tenant requests lease
        LeaseContract c = service.createLeaseRequest(tenant, shop, new StandardLeaseFactory(), 8000.0);
        assertEquals(ContractStatus.PENDING_APPROVAL, c.getStatus());

        // 2. Manager approves
        assertTrue(manager.permitRequest(c));
        manager.applyApproveContractService(c, service);
        assertEquals(ContractStatus.ACTIVE, c.getStatus());

        // 3. Tenant uploads sales
        Payment p = service.submitMonthlySales(c, "2026-04", 15000.0, new ProgressiveCommissionStrategy());
        // commission: 10000*0.08 + 5000*0.09 = 800 + 450 = 1250; total = 8000 + 1250 = 9250
        assertEquals(9250.0, p.getAmount(), 0.001);

        // 4. Accounting confirms
        assertTrue(accounting.confirmPaymentTransfer(p));
        assertEquals(PaymentStatus.CONFIRMED, p.getStatus());
    }
}
