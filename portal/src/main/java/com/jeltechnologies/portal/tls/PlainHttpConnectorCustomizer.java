package com.jeltechnologies.portal.tls;

import com.jeltechnologies.portal.config.AcmeProperties;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Adds a second, plain-HTTP Tomcat connector alongside the main HTTPS one, solely so
 * Let's Encrypt can reach the HTTP-01 challenge path. {@code PortalAuthFilter} makes sure
 * nothing else is actually served on it.
 */
@Component
public class PlainHttpConnectorCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final AcmeProperties acmeProperties;

    public PlainHttpConnectorCustomizer(AcmeProperties acmeProperties) {
        this.acmeProperties = acmeProperties;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setScheme("http");
        connector.setSecure(false);
        connector.setPort(acmeProperties.httpPort());
        factory.addAdditionalConnectors(connector);
    }
}
