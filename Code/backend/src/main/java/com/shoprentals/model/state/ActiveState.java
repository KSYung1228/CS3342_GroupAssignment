package com.shoprentals.model.state;

import com.shoprentals.model.LeaseContract;

public class ActiveState implements ContractState {
    @Override
    public void submit(LeaseContract contract) {
        throw new IllegalStateException("Active contract cannot be resubmitted.");
    }
    @Override
    public void approve(LeaseContract contract) {
        throw new IllegalStateException("Contract is already active.");
    }
    @Override
    public void terminate(LeaseContract contract) {
        contract.setContractState("TERMINATED");
        contract.setStatus("TERMINATED");
    }
    @Override
    public void expire(LeaseContract contract) {
        contract.setContractState("EXPIRED");
        contract.setStatus("EXPIRED");
    }
    @Override
    public String getStateName() { return "ACTIVE"; }
}
