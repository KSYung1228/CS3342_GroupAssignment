package com.shoprentals.model.state;

import com.shoprentals.model.LeaseContract;

public class ExpiredState implements ContractState {
    @Override
    public void submit(LeaseContract c) { throw new IllegalStateException("Expired."); }
    @Override
    public void approve(LeaseContract c) { throw new IllegalStateException("Expired."); }
    @Override
    public void terminate(LeaseContract c) { throw new IllegalStateException("Expired."); }
    @Override
    public void expire(LeaseContract c) { throw new IllegalStateException("Already expired."); }
    @Override
    public String getStateName() { return "EXPIRED"; }
}
