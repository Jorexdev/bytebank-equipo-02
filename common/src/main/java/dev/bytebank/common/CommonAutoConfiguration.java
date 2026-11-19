package dev.bytebank.common;

import dev.bytebank.common.error.GlobalExceptionHandler;
import dev.bytebank.common.http.InternalClients;
import dev.bytebank.common.security.JwtVerifier;
import dev.bytebank.common.security.SecurityConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties(BytebankProperties.class)
@Import({ SecurityConfig.class, JwtVerifier.class, GlobalExceptionHandler.class, InternalClients.class,
        DemoProfileGuard.class })
public class CommonAutoConfiguration {
}
