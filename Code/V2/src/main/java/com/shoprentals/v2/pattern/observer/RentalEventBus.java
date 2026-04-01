package com.shoprentals.v2.pattern.observer;

import java.util.ArrayList;
import java.util.List;

public class RentalEventBus {
    private final List<RentalEventListener> listeners = new ArrayList<>();

    public void subscribe(RentalEventListener listener) { listeners.add(listener); }

    public void publish(RentalEvent event) {
        for (RentalEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }
}
