package com.servicedesk.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sla")
public class SlaProperties {
    private Duration targetDuration = Duration.ofHours(24);

    public Duration getTargetDuration() {
        return targetDuration;
    }

    public void setTargetDuration(Duration targetDuration) {
        if (targetDuration == null || targetDuration.isZero() || targetDuration.isNegative()) {
            throw new IllegalArgumentException("The SLA target duration must be greater than zero.");
        }
        this.targetDuration = targetDuration;
    }
}
