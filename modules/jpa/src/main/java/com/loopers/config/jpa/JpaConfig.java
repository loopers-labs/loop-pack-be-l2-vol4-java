package com.loopers.config.jpa;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EntityScan({"com.loopers"})
@EnableJpaRepositories(
    basePackages = {"com.loopers"},
    excludeFilters = @Filter(
        type = FilterType.REGEX,
        pattern = "com\\.loopers\\.(?![^.]+\\.infrastructure\\.).*"))
public class JpaConfig {
}
