package com.servicedesk.sla;

import com.servicedesk.request.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlaEscalationJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlaEscalationJob.class);

    private final RequestService requests;

    public SlaEscalationJob(RequestService requests) {
        this.requests = requests;
    }

    @Scheduled(fixedDelayString = "${app.sla.escalation-interval:60000}")
    public void escalateBreaches() {
        int escalated = requests.escalateOverdue();
        if (escalated > 0) {
            LOGGER.warn("Escalated {} service request(s) after SLA breach.", escalated);
        }
    }
}
