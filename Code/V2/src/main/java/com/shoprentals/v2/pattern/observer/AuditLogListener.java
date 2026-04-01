package com.shoprentals.v2.pattern.observer;

public class AuditLogListener implements RentalEventListener {
    @Override
    public void onEvent(RentalEvent event) {
        System.out.println("[AUDIT] " + event.getTimestamp() + " | " + event.getType() + " | " + event.getMessage());
    }
}
