package com.aerovhyn.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.aerovhyn")
@EnableCaching
@EnableScheduling
@EnableAsync
public class AerovhynApplication {

    private static final Logger log = LoggerFactory.getLogger(AerovhynApplication.class);
    private static final String DEFAULT_JWT_SECRET = "0123456789012345678901234567890123456789012345678901234567890123";

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AerovhynApplication.class);
        app.addListeners((org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent event) -> {
            Environment env = event.getEnvironment();
            String jwtSecret = env.getProperty("aerovhyn.jwt.secret");
            if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
                log.warn("""
                        \s
                        !!! SECURITY WARNING: Using default JWT secret configured in application.yml.
                        !!! Set the AEROVHYN_JWT_SECRET environment variable in production.
                        !!! Anyone with access to this default can forge authentication tokens.
                        """);
            }
        });
        app.run(args);
    }
}
