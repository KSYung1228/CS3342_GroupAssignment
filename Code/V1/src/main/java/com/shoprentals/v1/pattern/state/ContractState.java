package com.shoprentals.v1.pattern.state;

import com.shoprentals.v1.model.ContractStatus;

public interface ContractState {
    ContractStatus getStatus();

    ContractState requestApproval();

    ContractState approve();

    ContractState terminate();

    ContractState expire();
}
