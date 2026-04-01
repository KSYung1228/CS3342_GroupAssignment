package com.shoprentals.v2.model.state;

import com.shoprentals.v2.model.ContractStatus;

public class PendingApprovalState extends BaseContractState {
    @Override public ContractStatus getStatus() { return ContractStatus.PENDING_APPROVAL; }
    @Override public ContractState approve() { return new ActiveState(); }
    @Override public ContractState terminate() { return new TerminatedState(); }
}
