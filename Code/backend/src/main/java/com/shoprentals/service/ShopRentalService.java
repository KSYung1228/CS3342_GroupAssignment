package com.shoprentals.service;

import com.shoprentals.model.*;
import com.shoprentals.pattern.observer.*;
import com.shoprentals.pattern.strategy.*;
import com.shoprentals.pattern.factory.*;
import com.shoprentals.model.state.*;
import com.shoprentals.repository.DataRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Core service layer – orchestrates all business operations.
 */
@Service
public class ShopRentalService {

    private final DataRepository repo;
    private final RentalEventBus eventBus;

    public ShopRentalService(DataRepository repo, RentalEventBus eventBus,
                             AuditLogListener auditLog) {
        this.repo = repo;
        this.eventBus = eventBus;
        eventBus.register(auditLog);
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    public Optional<Admin> loginAdmin(String username, String password) {
        return repo.getAdmins().values().stream()
            .filter(a -> a.getUsername().equals(username)
                      && a.getPasswordHash().equals(password)
                      && "ACTIVE".equals(a.getStatus()))
            .findFirst();
    }

    public Optional<Merchant> loginMerchant(String email, String password) {
        return repo.getMerchants().values().stream()
            .filter(m -> m.getEmail().equals(email)
                      && m.getPasswordHash().equals(password)
                      && "APPROVED".equals(m.getStatus()))
            .findFirst();
    }

    // ── Buildings ─────────────────────────────────────────────────────────────

    public List<Building> getAllBuildings() {
        return new ArrayList<>(repo.getBuildings().values());
    }

    public Building getBuilding(String id) {
        return repo.getBuildings().get(id);
    }

    public Building saveBuilding(Building b) {
        if (b.getId() == null || b.getId().isBlank()) b.setId(UUID.randomUUID().toString());
        repo.getBuildings().put(b.getId(), b);
        repo.save();
        return b;
    }

    // ── Shop Types ────────────────────────────────────────────────────────────

    public List<ShopType> getAllShopTypes() {
        return new ArrayList<>(repo.getShopTypes().values());
    }

    public ShopType getShopType(String id) {
        return repo.getShopTypes().get(id);
    }

    public ShopType saveShopType(ShopType st) {
        if (st.getId() == null || st.getId().isBlank()) st.setId(UUID.randomUUID().toString());
        repo.getShopTypes().put(st.getId(), st);
        repo.save();
        return st;
    }

    // ── Shops ─────────────────────────────────────────────────────────────────

    public List<Shop> getAllShops() {
        return new ArrayList<>(repo.getShops().values());
    }

    public List<Shop> getShopsByBuilding(String buildingId) {
        return repo.getShops().values().stream()
            .filter(s -> buildingId.equals(s.getBuildingId()))
            .toList();
    }

    public Shop getShop(String id) {
        return repo.getShops().get(id);
    }

    /** Manager edits a shop (including grid position) via the floor-plan editor. */
    public Shop saveShop(Shop shop) {
        if (shop.getId() == null || shop.getId().isBlank()) shop.setId(UUID.randomUUID().toString());
        // Validate shop type is allowed in its building
        Building bld = repo.getBuildings().get(shop.getBuildingId());
        if (bld != null && shop.getShopTypeId() != null
                && !bld.isShopTypeAllowed(shop.getShopTypeId())) {
            throw new IllegalArgumentException(
                "ShopType " + shop.getShopTypeId() + " is not allowed in building " + bld.getName());
        }
        repo.getShops().put(shop.getId(), shop);
        repo.save();
        return shop;
    }

    // ── Merchants ─────────────────────────────────────────────────────────────

    public List<Merchant> getAllMerchants() {
        return new ArrayList<>(repo.getMerchants().values());
    }

    public Merchant getMerchant(String id) {
        return repo.getMerchants().get(id);
    }

    public Merchant registerMerchant(Merchant m) {
        m.setId(UUID.randomUUID().toString());
        m.setStatus("PENDING");
        repo.getMerchants().put(m.getId(), m);
        repo.save();
        return m;
    }

    public Merchant approveMerchant(String merchantId, boolean approved) {
        Merchant m = repo.getMerchants().get(merchantId);
        if (m == null) throw new NoSuchElementException("Merchant not found: " + merchantId);
        m.setStatus(approved ? "APPROVED" : "REJECTED");
        repo.save();
        eventBus.publish(new RentalEvent(
            approved ? RentalEvent.Type.MERCHANT_APPROVED : RentalEvent.Type.MERCHANT_REJECTED,
            merchantId, m.getName() + " has been " + m.getStatus().toLowerCase()));
        return m;
    }

    // ── Lease Contracts ───────────────────────────────────────────────────────

    public List<LeaseContract> getAllContracts() {
        return new ArrayList<>(repo.getContracts().values());
    }

    public List<LeaseContract> getContractsByMerchant(String merchantId) {
        return repo.getContracts().values().stream()
            .filter(c -> merchantId.equals(c.getMerchantId()))
            .toList();
    }

    public LeaseContract getContract(String id) {
        return repo.getContracts().get(id);
    }

    /**
     * Creates a new lease contract using the Factory + Strategy patterns.
     *
     * @param type  "STANDARD" or "SHORT_TERM"
     * @param strat "STANDARD", "PROMOTIONAL:<rate>", "PREMIUM:<rate>"
     */
    public LeaseContract createContract(String merchantId, String shopId,
                                        LocalDate start, LocalDate end,
                                        String type, String strat) {
        Shop shop = repo.getShops().get(shopId);
        if (shop == null) throw new NoSuchElementException("Shop not found: " + shopId);
        ShopType shopType = repo.getShopTypes().get(shop.getShopTypeId());
        if (shopType == null) throw new NoSuchElementException("ShopType not found");

        LeaseContractFactory factory = "SHORT_TERM".equalsIgnoreCase(type)
            ? new ShortTermLeaseFactory() : new StandardLeaseFactory();

        RentCalculationStrategy strategy = parseStrategy(strat);
        LeaseContract contract = factory.create(merchantId, shop, shopType, start, end, strategy);

        repo.getContracts().put(contract.getId(), contract);
        repo.save();
        return contract;
    }

    private RentCalculationStrategy parseStrategy(String strat) {
        if (strat == null || strat.startsWith("STANDARD")) return new StandardRentStrategy();
        if (strat.startsWith("PROMOTIONAL:")) {
            double rate = Double.parseDouble(strat.split(":")[1]);
            return new PromotionalRentStrategy(rate);
        }
        if (strat.startsWith("PREMIUM:")) {
            double rate = Double.parseDouble(strat.split(":")[1]);
            return new PremiumRentStrategy(rate);
        }
        return new StandardRentStrategy();
    }

    public LeaseContract submitContract(String contractId) {
        LeaseContract c = requireContract(contractId);
        ContractState state = ContractStateFactory.of(c.getContractState());
        state.submit(c);
        repo.save();
        eventBus.publish(new RentalEvent(RentalEvent.Type.CONTRACT_SUBMITTED, contractId,
            "Contract submitted for approval"));
        return c;
    }

    public LeaseContract approveContract(String contractId, String adminId) {
        LeaseContract c = requireContract(contractId);
        ContractState state = ContractStateFactory.of(c.getContractState());
        state.approve(c);
        c.setAdminId(adminId);
        // Mark shop as OCCUPIED
        Shop shop = repo.getShops().get(c.getShopId());
        if (shop != null) { shop.setStatus("OCCUPIED"); }
        repo.save();
        eventBus.publish(new RentalEvent(RentalEvent.Type.CONTRACT_APPROVED, contractId,
            "Contract approved by admin " + adminId));
        return c;
    }

    public LeaseContract terminateContract(String contractId) {
        LeaseContract c = requireContract(contractId);
        ContractState state = ContractStateFactory.of(c.getContractState());
        state.terminate(c);
        // Free the shop
        Shop shop = repo.getShops().get(c.getShopId());
        if (shop != null) { shop.setStatus("AVAILABLE"); }
        repo.save();
        eventBus.publish(new RentalEvent(RentalEvent.Type.CONTRACT_TERMINATED, contractId,
            "Contract terminated"));
        return c;
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    public List<PaymentRecord> getAllPayments() {
        return new ArrayList<>(repo.getPayments().values());
    }

    public List<PaymentRecord> getPaymentsByContract(String contractId) {
        return repo.getPayments().values().stream()
            .filter(p -> contractId.equals(p.getContractId()))
            .toList();
    }

    public PaymentRecord createPayment(String contractId, double amount, String method) {
        LeaseContract c = requireContract(contractId);
        PaymentRecord p = new PaymentRecord();
        p.setId(UUID.randomUUID().toString());
        p.setContractId(contractId);
        p.setAmount(amount);
        p.setPayTime(LocalDateTime.now());
        p.setPayMethod(method);
        p.setStatus("PENDING");
        repo.getPayments().put(p.getId(), p);
        repo.save();
        return p;
    }

    public PaymentRecord confirmPayment(String paymentId) {
        PaymentRecord p = requirePayment(paymentId);
        p.setStatus("CONFIRMED");
        repo.save();
        eventBus.publish(new RentalEvent(RentalEvent.Type.PAYMENT_CONFIRMED, paymentId,
            "Payment of HKD " + p.getAmount() + " confirmed"));
        return p;
    }

    public PaymentRecord refundPayment(String paymentId) {
        PaymentRecord p = requirePayment(paymentId);
        p.setStatus("REFUNDED");
        repo.save();
        eventBus.publish(new RentalEvent(RentalEvent.Type.PAYMENT_REFUNDED, paymentId,
            "Payment refunded"));
        return p;
    }

    // ── Accounting: adjust rent ───────────────────────────────────────────────

    /**
     * Accounting use-case: alter the rent based on store status.
     * Recalculates using a new strategy and updates the active contract.
     */
    public LeaseContract adjustRent(String contractId, String newStrategyStr) {
        LeaseContract c = requireContract(contractId);
        Shop shop = repo.getShops().get(c.getShopId());
        ShopType shopType = repo.getShopTypes().get(shop.getShopTypeId());
        RentCalculationStrategy strategy = parseStrategy(newStrategyStr);
        c.setRentPerMonth(strategy.calculate(shop, shopType));
        repo.save();
        return c;
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    public Map<String, Object> generateReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        long totalShops     = repo.getShops().size();
        long occupiedShops  = repo.getShops().values().stream().filter(s -> "OCCUPIED".equals(s.getStatus())).count();
        double totalRevenue = repo.getPayments().values().stream()
            .filter(p -> "CONFIRMED".equals(p.getStatus()))
            .mapToDouble(PaymentRecord::getAmount).sum();
        report.put("totalShops",     totalShops);
        report.put("occupiedShops",  occupiedShops);
        report.put("availableShops", totalShops - occupiedShops);
        report.put("occupancyRate",  totalShops == 0 ? 0 : (double) occupiedShops / totalShops);
        report.put("totalRevenue",   totalRevenue);
        report.put("totalContracts", repo.getContracts().size());
        report.put("pendingMerchants", repo.getMerchants().values().stream()
            .filter(m -> "PENDING".equals(m.getStatus())).count());
        return report;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LeaseContract requireContract(String id) {
        LeaseContract c = repo.getContracts().get(id);
        if (c == null) throw new NoSuchElementException("Contract not found: " + id);
        return c;
    }

    private PaymentRecord requirePayment(String id) {
        PaymentRecord p = repo.getPayments().get(id);
        if (p == null) throw new NoSuchElementException("Payment not found: " + id);
        return p;
    }
}
