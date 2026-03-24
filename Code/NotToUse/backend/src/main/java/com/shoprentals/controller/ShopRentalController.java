package com.shoprentals.controller;

import com.shoprentals.facade.ShopRentalFacade;
import com.shoprentals.model.*;
import com.shoprentals.pattern.observer.AuditLogListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Main REST controller – exposes all API endpoints.
 * Uses the Facade to keep controllers thin.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ShopRentalController {

    private final ShopRentalFacade facade;
    private final AuditLogListener auditLog;

    public ShopRentalController(ShopRentalFacade facade, AuditLogListener auditLog) {
        this.facade = facade;
        this.auditLog = auditLog;
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    @PostMapping("/auth/admin")
    public ResponseEntity<?> adminLogin(@RequestBody Map<String, String> body) {
        Optional<Admin> admin = facade.adminLogin(body.get("username"), body.get("password"));
        return admin.<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(401).body(Map.of("error", "Invalid credentials")));
    }

    @PostMapping("/auth/merchant")
    public ResponseEntity<?> merchantLogin(@RequestBody Map<String, String> body) {
        Optional<Merchant> merchant = facade.merchantLogin(body.get("email"), body.get("password"));
        return merchant.<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElse(ResponseEntity.status(401).body(Map.of("error", "Invalid credentials")));
    }

    // ── Buildings ─────────────────────────────────────────────────────────────

    @GetMapping("/buildings")
    public List<Building> listBuildings() { return facade.listBuildings(); }

    @GetMapping("/buildings/{id}")
    public ResponseEntity<Building> getBuilding(@PathVariable String id) {
        Building b = facade.getBuilding(id);
        return b != null ? ResponseEntity.ok(b) : ResponseEntity.notFound().build();
    }

    @PostMapping("/buildings")
    public Building saveBuilding(@RequestBody Building building) {
        return facade.saveBuilding(building);
    }

    // ── Shop Types ────────────────────────────────────────────────────────────

    @GetMapping("/shop-types")
    public List<ShopType> listShopTypes() { return facade.listShopTypes(); }

    @PostMapping("/shop-types")
    public ShopType saveShopType(@RequestBody ShopType shopType) {
        return facade.saveShopType(shopType);
    }

    // ── Shops ─────────────────────────────────────────────────────────────────

    @GetMapping("/shops")
    public List<Shop> listShops() { return facade.listShops(); }

    @GetMapping("/shops/{id}")
    public ResponseEntity<Shop> getShop(@PathVariable String id) {
        Shop s = facade.getShop(id);
        return s != null ? ResponseEntity.ok(s) : ResponseEntity.notFound().build();
    }

    /** Floor-plan for a building (used by the grid UI). */
    @GetMapping("/buildings/{buildingId}/floor-plan")
    public List<Shop> getFloorPlan(@PathVariable String buildingId) {
        return facade.getFloorPlan(buildingId);
    }

    /** Manager updates a shop via the grid editor. */
    @PutMapping("/shops/{id}")
    public ResponseEntity<?> updateShop(@PathVariable String id, @RequestBody Shop shop) {
        shop.setId(id);
        try {
            return ResponseEntity.ok(facade.updateShopFromGrid(shop));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/shops")
    public ResponseEntity<?> createShop(@RequestBody Shop shop) {
        try {
            return ResponseEntity.ok(facade.updateShopFromGrid(shop));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Merchants ─────────────────────────────────────────────────────────────

    @GetMapping("/merchants")
    public List<Merchant> listMerchants() { return facade.listMerchants(); }

    @PostMapping("/merchants/register")
    public Merchant register(@RequestBody Merchant merchant) {
        return facade.registerMerchant(merchant);
    }

    @PutMapping("/merchants/{id}/approve")
    public Merchant approveMerchant(@PathVariable String id,
                                    @RequestParam(defaultValue = "true") boolean approved) {
        return facade.approveMerchant(id, approved);
    }

    // ── Contracts ─────────────────────────────────────────────────────────────

    @GetMapping("/contracts")
    public List<LeaseContract> listContracts() { return facade.listContracts(); }

    @GetMapping("/contracts/merchant/{merchantId}")
    public List<LeaseContract> myContracts(@PathVariable String merchantId) {
        return facade.myContracts(merchantId);
    }

    @PostMapping("/contracts")
    public ResponseEntity<?> createContract(@RequestBody Map<String, String> body) {
        try {
            LeaseContract c = facade.applyForLease(
                body.get("merchantId"), body.get("shopId"),
                LocalDate.parse(body.get("startTime")),
                LocalDate.parse(body.get("endTime")));
            return ResponseEntity.ok(c);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/contracts/{id}/submit")
    public ResponseEntity<?> submitContract(@PathVariable String id) {
        try { return ResponseEntity.ok(facade.submitLease(id)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @PutMapping("/contracts/{id}/approve")
    public ResponseEntity<?> approveContract(@PathVariable String id,
                                             @RequestParam String adminId) {
        try { return ResponseEntity.ok(facade.approveLease(id, adminId)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @PutMapping("/contracts/{id}/terminate")
    public ResponseEntity<?> terminateContract(@PathVariable String id) {
        try { return ResponseEntity.ok(facade.terminateLease(id)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    /** Accounting: adjust rent by strategy. */
    @PutMapping("/contracts/{id}/adjust-rent")
    public ResponseEntity<?> adjustRent(@PathVariable String id,
                                        @RequestParam String strategy) {
        try { return ResponseEntity.ok(facade.adjustRent(id, strategy)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    // ── Payments ─────────────────────────────────────────────────────────────

    @GetMapping("/payments")
    public List<PaymentRecord> listPayments() { return facade.listPayments(); }

    @GetMapping("/payments/contract/{contractId}")
    public List<PaymentRecord> contractPayments(@PathVariable String contractId) {
        return facade.contractPayments(contractId);
    }

    @PostMapping("/payments")
    public ResponseEntity<?> createPayment(@RequestBody Map<String, String> body) {
        try {
            PaymentRecord p = facade.makePayment(
                body.get("contractId"),
                Double.parseDouble(body.get("amount")),
                body.get("payMethod"));
            return ResponseEntity.ok(p);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/payments/{id}/confirm")
    public ResponseEntity<?> confirmPayment(@PathVariable String id) {
        try { return ResponseEntity.ok(facade.confirmPayment(id)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @PutMapping("/payments/{id}/refund")
    public ResponseEntity<?> refundPayment(@PathVariable String id) {
        try { return ResponseEntity.ok(facade.refundPayment(id)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    @GetMapping("/reports")
    public Map<String, Object> getReport() { return facade.getReport(); }

    @GetMapping("/audit-log")
    public List<String> getAuditLog() { return auditLog.getLog(); }
}
