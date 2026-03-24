package com.shoprentals.model.state;

import com.shoprentals.model.LeaseContract;

public class TerminatedState implements ContractState {
    @Override
    public void submit(LeaseContract c) { throw new IllegalStateException("Terminated."); }
    @Override
    public void approve(LeaseContract c) { throw new IllegalStateException("Terminated."); }
    @Override
    public void terminate(LeaseContract c) { throw new IllegalStateException("Already terminated."); }
    @Override
    public void expire(LeaseContract c) { throw new IllegalStateException("Terminated."); }
    @Override
    public String getStateName() { return "TERMINATED"; }
}
