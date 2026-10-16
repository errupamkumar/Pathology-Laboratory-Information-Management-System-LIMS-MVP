package com.lims.server.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Spring Modulith 1.2 keeps completed event publications indefinitely; prune them nightly.
 * Incomplete publications (failed listeners) are kept and re-delivered on restart.
 */
@Component
public class EventPublicationMaintenance {

    private static final Logger log = LoggerFactory.getLogger(EventPublicationMaintenance.class);

    private final CompletedEventPublications completedPublications;
    private final Duration retention;

    public EventPublicationMaintenance(CompletedEventPublications completedPublications,
                                       @Value("${lims.events.completed-retention:P7D}") Duration retention) {
        this.completedPublications = completedPublications;
        this.retention = retention;
    }

    @Scheduled(cron = "${lims.events.cleanup-cron:0 30 3 * * *}")
    public void purgeCompletedPublications() {
        completedPublications.deletePublicationsOlderThan(retention);
        log.info("Purged completed event publications older than {}", retention);
    }
}
