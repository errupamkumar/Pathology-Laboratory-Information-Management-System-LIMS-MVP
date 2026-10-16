package com.lims.server.observability;

import com.lims.common.enums.SampleStatus;
import com.lims.common.event.OrderAuthorizedEvent;
import com.lims.common.event.OrderCreatedEvent;
import com.lims.common.event.ResultEvaluatedEvent;
import com.lims.common.event.SampleAccessionedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Laboratory KPIs exported to Prometheus ({@code /actuator/prometheus}).
 * <p>
 * Plain {@code @EventListener}s on purpose: transactional listeners are recorded in the Modulith
 * event publication table, which would add a row per counter increment.
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;
    private final Counter ordersCreated;
    private final Counter reportsAuthorized;
    private final Counter criticalResults;
    private final Counter deltaFlags;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
        // Not "lims.orders.created": the Prometheus client reserves the _created suffix and would drop it
        this.ordersCreated = Counter.builder("lims.orders.booked").description("Diagnostic orders booked").register(registry);
        this.reportsAuthorized = Counter.builder("lims.reports.authorized").description("Reports signed off by a pathologist").register(registry);
        this.criticalResults = Counter.builder("lims.results.critical").description("Results outside panic limits").register(registry);
        this.deltaFlags = Counter.builder("lims.results.delta.flagged").description("Results flagged by the delta check").register(registry);
    }

    @EventListener
    public void on(OrderCreatedEvent event) {
        ordersCreated.increment();
    }

    @EventListener
    public void on(OrderAuthorizedEvent event) {
        reportsAuthorized.increment();
    }

    @EventListener
    public void on(ResultEvaluatedEvent event) {
        if (event.isCritical()) {
            criticalResults.increment();
        }
        if (event.requiresReview()) {
            deltaFlags.increment();
        }
    }

    @EventListener
    public void on(SampleAccessionedEvent event) {
        if (event.status() == SampleStatus.REJECTED) {
            registry.counter("lims.samples.rejected", "reason", String.valueOf(event.rejectionReason())).increment();
        } else {
            registry.counter("lims.samples.received").increment();
        }
    }
}
