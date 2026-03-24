package com.shoprentals.v1.pattern.state;

import com.shoprentals.v1.model.ContractStatus;

public class TerminatedState extends BaseContractState {
    @Override
    public ContractStatus getStatus() {
        return ContractStatus.TERMINATED;
    }
}
