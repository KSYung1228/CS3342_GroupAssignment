package com.shoprentals.v1.pattern.state;

import com.shoprentals.v1.model.ContractStatus;

public class DraftState extends BaseContractState {
    @Override
    public ContractStatus getStatus() {
        return ContractStatus.DRAFT;
    }

    @Override
    public ContractState requestApproval() {
        return new PendingApprovalState();
    }
}
