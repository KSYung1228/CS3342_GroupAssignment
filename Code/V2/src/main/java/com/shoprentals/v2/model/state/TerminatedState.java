package com.shoprentals.v2.model.state;

import com.shoprentals.v2.model.ContractStatus;

public class TerminatedState extends BaseContractState {
    @Override public ContractStatus getStatus() { return ContractStatus.TERMINATED; }
}
