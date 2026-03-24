package com.shoprentals.model.state;

import com.shoprentals.model.LeaseContract;

public class PendingApprovalState implements ContractState {
    @Override
    public void submit(LeaseContract contract) {
        throw new IllegalStateException("Contract already submitted.");
    }
    @Override
    public void approve(LeaseContract contract) {
        contract.setContractState("ACTIVE");
        contract.setStatus("ACTIVE");
    }
    @Override
    public void terminate(LeaseContract contract) {
        contract.setContractState("TERMINATED");
        contract.setStatus("TERMINATED");
    }
    @Override
    public void expire(LeaseContract contract) {
        throw new IllegalStateException("Pending contracts do not expire.");
    }
    @Override
    public String getStateName() { return "PENDING_APPROVAL"; }
}
