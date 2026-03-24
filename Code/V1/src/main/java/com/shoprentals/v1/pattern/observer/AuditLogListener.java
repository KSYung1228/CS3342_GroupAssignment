package com.shoprentals.v1.pattern.observer;

public class AuditLogListener implements RentalEventListener {
    @Override
    public void onEvent(RentalEvent event) {
        System.out.printf("[AUDIT] %s | %s | %s%n", event.getTimestamp(), event.getType(), event.getMessage());
    }
}
