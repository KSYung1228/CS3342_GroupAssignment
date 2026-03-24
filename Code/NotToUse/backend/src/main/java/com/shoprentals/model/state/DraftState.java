package com.shoprentals.model.state;

import com.shoprentals.model.LeaseContract;

public class DraftState implements ContractState {
    @Override
    public void submit(LeaseContract contract) {
        contract.setContractState("PENDING_APPROVAL");
        contract.setStatus("PENDING");
    }
    @Override
    public void approve(LeaseContract contract) {
        throw new IllegalStateException("Cannot approve a draft. Submit it first.");
    }
    @Override
    public void terminate(LeaseContract contract) {
        throw new IllegalStateException("Cannot terminate a draft contract.");
    }
    @Override
    public void expire(LeaseContract contract) {
        throw new IllegalStateException("Draft contracts do not expire.");
    }
    @Override
    public String getStateName() { return "DRAFT"; }
}
