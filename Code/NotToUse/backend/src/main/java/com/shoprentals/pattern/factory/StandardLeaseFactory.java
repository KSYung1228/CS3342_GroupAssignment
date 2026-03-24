package com.shoprentals.pattern.factory;

import com.shoprentals.model.LeaseContract;

/** Produces a standard 1-year lease with 2-month deposit. */
public class StandardLeaseFactory extends LeaseContractFactory {
    @Override
    protected LeaseContract createContract() {
        LeaseContract c = new LeaseContract();
        c.setRentCycle("MONTHLY");
        return c;
    }
    @Override
    protected int depositMonths() { return 2; }
}
