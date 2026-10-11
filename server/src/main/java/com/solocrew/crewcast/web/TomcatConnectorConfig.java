package com.solocrew.crewcast.web;

import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;

@Configuration
public class TomcatConnectorConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TomcatConnectorConfig.class);

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatSoLingerCustomizer() {
        return factory -> {
            factory.addProtocolHandlerCustomizers(protocolHandlerCustomizer());
            factory.addConnectorCustomizers(connector -> {
                ProtocolHandler handler = connector.getProtocolHandler();
                connector.setProperty("soLingerOn", "false");
                connector.setProperty("soLingerTime", "0");
                if (handler instanceof AbstractProtocol<?> protocol) {
                    disableSocketSoLinger(protocol);
                    logSocketLingerState("connector", protocol);
                }
            });
        };
    }

    private static TomcatProtocolHandlerCustomizer<?> protocolHandlerCustomizer() {
        return protocolHandler -> {
            if (protocolHandler instanceof AbstractProtocol<?> protocol) {
                disableSocketSoLinger(protocol);
                logSocketLingerState("protocol-handler", protocol);
            }
        };
    }

    /**
     * Never call {@link AbstractProtocol#setConnectionLinger(int)} with {@code -1}: Tomcat stores
     * {@code -1} as linger time and {@code SocketProperties#setProperties(Socket)} invokes
     * {@code setSoLinger(false, -1)}, which throws on macOS with recent JDKs.
     */
    static void disableSocketSoLinger(AbstractProtocol<?> protocol) {
        try {
            AbstractEndpoint<?, ?> endpoint = endpoint(protocol);
            SocketProperties socketProperties = socketProperties(endpoint);
            socketProperties.setSoLingerOn(false);
            socketProperties.setSoLingerTime(0);
            endpoint.setProperty("soLingerOn", "false");
            endpoint.setProperty("soLingerTime", "0");
        } catch (ReflectiveOperationException e) {
            LOG.warn("Could not disable Tomcat SO_LINGER: {}", e.toString());
        }
    }

    private static void logSocketLingerState(String phase, AbstractProtocol<?> protocol) {
        try {
            SocketProperties sp = socketProperties(endpoint(protocol));
            LOG.info(
                    "Crewcast Tomcat {}: handlerClass={} soLingerOn={} soLingerTime={}",
                    phase,
                    protocol.getClass().getName(),
                    readField(sp, "soLingerOn"),
                    readField(sp, "soLingerTime"));
        } catch (ReflectiveOperationException e) {
            LOG.warn("Crewcast Tomcat {}: could not read linger state: {}", phase, e.toString());
        }
    }

    private static AbstractEndpoint<?, ?> endpoint(AbstractProtocol<?> protocol) throws ReflectiveOperationException {
        Field endpointField = AbstractProtocol.class.getDeclaredField("endpoint");
        endpointField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AbstractEndpoint<?, ?> endpoint = (AbstractEndpoint<?, ?>) endpointField.get(protocol);
        return endpoint;
    }

    private static SocketProperties socketProperties(AbstractEndpoint<?, ?> endpoint) throws ReflectiveOperationException {
        Field spField = AbstractEndpoint.class.getDeclaredField("socketProperties");
        spField.setAccessible(true);
        return (SocketProperties) spField.get(endpoint);
    }

    private static Object readField(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
