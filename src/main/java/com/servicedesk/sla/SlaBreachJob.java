package com.servicedesk.sla;

import com.servicedesk.request.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlaBreachJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlaBreachJob.class);

    private final RequestService requests;

    public SlaBreachJob(RequestService requests) {
        this.requests = requests;
    }

    @Scheduled(fixedDelayString = "${app.sla.breach-check-interval:60000}")
    public void checkForBreaches() {
        int breached = requests.markSlaBreaches();
        if (breached > 0) {
            LOGGER.warn("Flagged {} service request(s) for attention after SLA breach.", breached);
        }
    }
}
