package com.shoprentals.v2.model.state;

import com.shoprentals.v2.model.ContractStatus;

public class DraftState extends BaseContractState {
    @Override public ContractStatus getStatus() { return ContractStatus.DRAFT; }
    @Override public ContractState requestApproval() { return new PendingApprovalState(); }
}
