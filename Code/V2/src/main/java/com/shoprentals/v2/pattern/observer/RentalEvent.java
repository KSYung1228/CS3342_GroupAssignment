package com.shoprentals.v2.pattern.observer;

import java.time.LocalDateTime;

public class RentalEvent {
    private final String type;
    private final String message;
    private final LocalDateTime timestamp;

    public RentalEvent(String type, String message) {
        this.type = type;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public String getType() { return type; }
    public String getMessage() { return message; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
