package com.shoprentals.pattern.observer;

/**
 * Observer pattern: listener that reacts to rental system events.
 */
public interface RentalEventListener {
    void onEvent(RentalEvent event);
}
