package com.shoprentals.pattern.observer;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Concrete observer: keeps an in-memory audit log of all events.
 * In a production system this would write to a database or file.
 */
@Component
public class AuditLogListener implements RentalEventListener {
    private final List<String> log = new ArrayList<>();

    @Override
    public void onEvent(RentalEvent event) {
        String entry = java.time.LocalDateTime.now() + " | " + event;
        log.add(entry);
        System.out.println("[AUDIT] " + entry);
    }

    public List<String> getLog() {
        return List.copyOf(log);
    }
}
