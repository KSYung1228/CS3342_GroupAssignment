package com.shoprentals.v2.model.state;

public abstract class BaseContractState implements ContractState {
    @Override
    public ContractState requestApproval() {
        throw new IllegalStateException("Cannot request approval from state: " + getStatus());
    }

    @Override
    public ContractState approve() {
        throw new IllegalStateException("Cannot approve from state: " + getStatus());
    }

    @Override
    public ContractState terminate() {
        throw new IllegalStateException("Cannot terminate from state: " + getStatus());
    }

    @Override
    public ContractState expire() {
        throw new IllegalStateException("Cannot expire from state: " + getStatus());
    }
}
