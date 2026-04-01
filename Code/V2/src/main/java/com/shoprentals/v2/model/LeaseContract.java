package com.shoprentals.v2.model;

import com.shoprentals.v2.model.state.*;

public class LeaseContract {
    private final int contractId;
    private final Shop shop;
    private final Tenant tenant;
    private ContractState state;
    private double baseRent;
    private double commissionRate;

    public LeaseContract(int contractId, Shop shop, Tenant tenant, double baseRent, double commissionRate) {
        this.contractId = contractId;
        this.shop = shop;
        this.tenant = tenant;
        this.baseRent = baseRent;
        this.commissionRate = commissionRate;
        this.state = new DraftState();
    }

    public double calculateTotalRent(double commissionAmount) {
        return baseRent + commissionAmount;
    }

    public void requestApproval() { this.state = this.state.requestApproval(); }
    public void approve()         { this.state = this.state.approve(); }
    public void terminate()       { this.state = this.state.terminate(); }
    public void expire()          { this.state = this.state.expire(); }

    public void restoreStatus(ContractStatus status) {
        this.state = switch (status) {
            case DRAFT            -> new DraftState();
            case PENDING_APPROVAL -> new PendingApprovalState();
            case ACTIVE           -> new ActiveState();
            case TERMINATED       -> new TerminatedState();
            case EXPIRED          -> new ExpiredState();
        };
    }

    public int getContractId()       { return contractId; }
    public Shop getShop()            { return shop; }
    public Tenant getTenant()        { return tenant; }
    public ContractStatus getStatus(){ return state.getStatus(); }
    public double getBaseRent()      { return baseRent; }
    public double getCommissionRate(){ return commissionRate; }
    public void setBaseRent(double v){ this.baseRent = v; }
    public void setCommissionRate(double v){ this.commissionRate = v; }
}
