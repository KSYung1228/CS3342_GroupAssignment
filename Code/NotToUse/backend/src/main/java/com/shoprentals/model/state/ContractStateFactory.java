package com.shoprentals.model.state;

import com.shoprentals.model.LeaseContract;

/**
 * Factory for rehydrating ContractState from the persisted string name.
 */
public class ContractStateFactory {
    public static ContractState of(String stateName) {
        if (stateName == null) return new DraftState();
        return switch (stateName) {
            case "PENDING_APPROVAL" -> new PendingApprovalState();
            case "ACTIVE"           -> new ActiveState();
            case "TERMINATED"       -> new TerminatedState();
            case "EXPIRED"          -> new ExpiredState();
            default                 -> new DraftState();
        };
    }
}
