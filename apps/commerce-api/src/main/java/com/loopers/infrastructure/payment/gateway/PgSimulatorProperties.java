package com.loopers.infrastructure.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "commerce.pg-simulator")
public class PgSimulatorProperties {
    private String baseUrl = "http://localhost:8082";
    private Duration connectTimeout = Duration.ofMillis(300);
    private Duration readTimeout = Duration.ofMillis(800);
    private String callbackUrl = "http://localhost:8080/api/v1/payments/callback";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }
}
