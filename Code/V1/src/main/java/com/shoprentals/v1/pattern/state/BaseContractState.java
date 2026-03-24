package com.shoprentals.v1.pattern.state;

public abstract class BaseContractState implements ContractState {
    protected IllegalStateException invalid(String action) {
        return new IllegalStateException(action + " is not allowed in state " + getStatus());
    }

    @Override
    public ContractState requestApproval() {
        throw invalid("requestApproval");
    }

    @Override
    public ContractState approve() {
        throw invalid("approve");
    }

    @Override
    public ContractState terminate() {
        throw invalid("terminate");
    }

    @Override
    public ContractState expire() {
        throw invalid("expire");
    }
}
