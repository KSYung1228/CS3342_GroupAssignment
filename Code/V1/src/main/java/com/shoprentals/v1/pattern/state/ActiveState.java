package com.shoprentals.v1.pattern.state;

import com.shoprentals.v1.model.ContractStatus;

public class ActiveState extends BaseContractState {
    @Override
    public ContractStatus getStatus() {
        return ContractStatus.ACTIVE;
    }

    @Override
    public ContractState terminate() {
        return new TerminatedState();
    }

    @Override
    public ContractState expire() {
        return new ExpiredState();
    }
}
