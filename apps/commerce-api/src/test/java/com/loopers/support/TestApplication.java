package com.loopers.support;

import com.loopers.CommerceApiApplication;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.util.TimeZone;

@SpringBootConfiguration
@EnableAutoConfiguration
@ConfigurationPropertiesScan
@ComponentScan(
    basePackages = "com.loopers",
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.loopers\\.testcontainers\\..*"
        ),
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = CommerceApiApplication.class
        )
    }
)
public class TestApplication {

    @PostConstruct
    public void started() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }
}
