package com.shoprentals.pattern.factory;

import com.shoprentals.model.LeaseContract;

/** Short-term lease (pop-up store) – monthly billing, 1-month deposit. */
public class ShortTermLeaseFactory extends LeaseContractFactory {
    @Override
    protected LeaseContract createContract() {
        LeaseContract c = new LeaseContract();
        c.setRentCycle("MONTHLY");
        return c;
    }
    @Override
    protected int depositMonths() { return 1; }
}
