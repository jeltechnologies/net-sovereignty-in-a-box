package com.jeltechnologies.portal;

import com.jeltechnologies.portal.config.PortalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PortalProperties.class)
public class PortalApplication {

    private static final Logger log = LoggerFactory.getLogger(PortalApplication.class);

    public static void main(String[] args) {
        try {
            SpringApplication.run(PortalApplication.class, args);
        } catch (Exception e) {
            log.error("Portal failed to start (identity not ready yet, or public IP could not be determined)", e);
            System.exit(1);
        }
    }
}
