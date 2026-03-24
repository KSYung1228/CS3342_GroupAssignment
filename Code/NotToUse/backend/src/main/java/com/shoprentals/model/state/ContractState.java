package com.shoprentals.model.state;

import com.shoprentals.model.LeaseContract;

/**
 * State pattern interface for LeaseContract lifecycle.
 */
public interface ContractState {
    void submit(LeaseContract contract);
    void approve(LeaseContract contract);
    void terminate(LeaseContract contract);
    void expire(LeaseContract contract);
    String getStateName();
}
