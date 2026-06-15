package com.loopers;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@TestConfiguration
@EnableAutoConfiguration
@ConfigurationPropertiesScan(basePackages = "com.loopers")
@ComponentScan(
    basePackages = "com.loopers",
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CommerceApiApplication.class),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.loopers\\.testcontainers\\..*")
    }
)
public class CommerceApiTestApplication {
}
