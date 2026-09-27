package com.servicedesk.reporting;

import com.servicedesk.request.RequestStatus;
import java.util.Map;

public record RequestReport(long total, Map<RequestStatus, Long> byStatus, long breachedOrEscalated) {
}
