package com.loopers.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pg")
public class PgProperties {
    private String simulatorUrl;
    private String callbackUrl;
}
