package com.shoprentals.pattern.observer;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Observable event bus – singleton bean that dispatches RentalEvents to all registered listeners.
 */
@Component
public class RentalEventBus {
    private final List<RentalEventListener> listeners = new ArrayList<>();

    public void register(RentalEventListener listener) {
        listeners.add(listener);
    }

    public void publish(RentalEvent event) {
        for (RentalEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }
}
