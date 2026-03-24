package com.shoprentals.pattern.observer;

/**
 * Immutable event object passed to all RentalEventListeners.
 */
public class RentalEvent {
    public enum Type {
        CONTRACT_SUBMITTED, CONTRACT_APPROVED, CONTRACT_TERMINATED, CONTRACT_EXPIRED,
        PAYMENT_CONFIRMED, PAYMENT_REFUNDED,
        MERCHANT_APPROVED, MERCHANT_REJECTED
    }

    private final Type type;
    private final String entityId;   // contract/payment/merchant id
    private final String message;

    public RentalEvent(Type type, String entityId, String message) {
        this.type = type;
        this.entityId = entityId;
        this.message = message;
    }

    public Type getType() { return type; }
    public String getEntityId() { return entityId; }
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "[" + type + "] " + entityId + " – " + message;
    }
}
