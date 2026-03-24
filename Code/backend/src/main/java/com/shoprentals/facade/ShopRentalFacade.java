package com.shoprentals.facade;

import com.shoprentals.model.*;
import com.shoprentals.service.ShopRentalService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Facade pattern: provides a simplified, high-level interface for the frontend controllers.
 * Hides the complexity of service interactions and pattern orchestration.
 */
@Component
public class ShopRentalFacade {

    private final ShopRentalService service;

    public ShopRentalFacade(ShopRentalService service) {
        this.service = service;
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    public Optional<Admin>    adminLogin(String username, String password) {
        return service.loginAdmin(username, password);
    }
    public Optional<Merchant> merchantLogin(String email, String password) {
        return service.loginMerchant(email, password);
    }

    // ── Floor plan ────────────────────────────────────────────────────────────

    /** Returns all shops in a building for floor-plan rendering. */
    public List<Shop> getFloorPlan(String buildingId) {
        return service.getShopsByBuilding(buildingId);
    }

    /** Manager saves a shop after editing it in the floor-plan grid. */
    public Shop updateShopFromGrid(Shop shop) {
        return service.saveShop(shop);
    }

    // ── Merchant lease flow ───────────────────────────────────────────────────

    public LeaseContract applyForLease(String merchantId, String shopId,
                                       LocalDate start, LocalDate end) {
        return service.createContract(merchantId, shopId, start, end, "STANDARD", "STANDARD");
    }

    public LeaseContract submitLease(String contractId) {
        return service.submitContract(contractId);
    }

    // ── Admin approval flow ───────────────────────────────────────────────────

    public LeaseContract approveLease(String contractId, String adminId) {
        return service.approveContract(contractId, adminId);
    }

    public LeaseContract terminateLease(String contractId) {
        return service.terminateContract(contractId);
    }

    public Merchant approveMerchant(String merchantId, boolean approved) {
        return service.approveMerchant(merchantId, approved);
    }

    // ── Accounting ────────────────────────────────────────────────────────────

    public LeaseContract adjustRent(String contractId, String strategy) {
        return service.adjustRent(contractId, strategy);
    }

    // ── Payments ─────────────────────────────────────────────────────────────

    public PaymentRecord makePayment(String contractId, double amount, String method) {
        return service.createPayment(contractId, amount, method);
    }
    public PaymentRecord confirmPayment(String paymentId) {
        return service.confirmPayment(paymentId);
    }
    public PaymentRecord refundPayment(String paymentId) {
        return service.refundPayment(paymentId);
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    public Map<String, Object> getReport() {
        return service.generateReport();
    }

    // ── Misc ─────────────────────────────────────────────────────────────────

    public List<Building> listBuildings()              { return service.getAllBuildings(); }
    public List<ShopType> listShopTypes()              { return service.getAllShopTypes(); }
    public List<Shop>     listShops()                  { return service.getAllShops(); }
    public List<Merchant> listMerchants()              { return service.getAllMerchants(); }
    public List<LeaseContract> listContracts()         { return service.getAllContracts(); }
    public List<PaymentRecord> listPayments()          { return service.getAllPayments(); }
    public List<LeaseContract> myContracts(String mid) { return service.getContractsByMerchant(mid); }
    public List<PaymentRecord> contractPayments(String cid){ return service.getPaymentsByContract(cid); }
    public Building getBuilding(String id)             { return service.getBuilding(id); }
    public Shop getShop(String id)                     { return service.getShop(id); }
    public ShopType saveShopType(ShopType st)          { return service.saveShopType(st); }
    public Building saveBuilding(Building b)           { return service.saveBuilding(b); }
    public Merchant registerMerchant(Merchant m)       { return service.registerMerchant(m); }
}
