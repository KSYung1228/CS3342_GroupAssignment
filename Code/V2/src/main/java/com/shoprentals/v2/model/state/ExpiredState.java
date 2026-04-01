package com.shoprentals.v2.model.state;

import com.shoprentals.v2.model.ContractStatus;

public class ExpiredState extends BaseContractState {
    @Override public ContractStatus getStatus() { return ContractStatus.EXPIRED; }
}
