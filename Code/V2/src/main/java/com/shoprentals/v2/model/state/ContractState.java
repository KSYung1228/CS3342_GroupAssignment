package com.shoprentals.v2.model.state;

import com.shoprentals.v2.model.ContractStatus;

public interface ContractState {
    ContractStatus getStatus();
    ContractState requestApproval();
    ContractState approve();
    ContractState terminate();
    ContractState expire();
}
