package com.lims.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.lims")
@EnableJpaRepositories(basePackages = "com.lims")
// @EntityScan replaces auto-configuration packages, so the Modulith event publication entity must be listed explicitly
@EntityScan(basePackages = {"com.lims", "org.springframework.modulith.events.jpa"})
@ConfigurationPropertiesScan(basePackages = "com.lims")
@EnableAsync
@EnableCaching
@EnableScheduling
@EnableJpaAuditing
public class LimsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LimsApplication.class, args);
    }
}
