package com.loopers;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootConfiguration
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
