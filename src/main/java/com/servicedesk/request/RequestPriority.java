package com.servicedesk.request;

public enum RequestPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT;

    public RequestPriority escalated() {
        return switch (this) {
            case LOW -> NORMAL;
            case NORMAL -> HIGH;
            case HIGH, URGENT -> URGENT;
        };
    }
}
