package com.loopers.support.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.loopers.utils.RestPageImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;

@Configuration
public class TestJacksonPageConfig {

    @Bean
    public Module jacksonPageModule() {
        return new SimpleModule().addAbstractTypeMapping(Page.class, RestPageImpl.class);
    }
}
