package com.solocrew.crewcast;

import com.solocrew.crewcast.web.TomcatConnectorConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(TomcatConnectorConfig.class)
public class CrewcastApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrewcastApplication.class, args);
    }
}
