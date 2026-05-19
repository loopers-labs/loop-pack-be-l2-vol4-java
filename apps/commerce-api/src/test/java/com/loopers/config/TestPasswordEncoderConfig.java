package com.loopers.config;

import com.loopers.domain.user.FakePasswordEncoder;
import com.loopers.domain.user.PasswordEncoder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestPasswordEncoderConfig {

    @Bean
    @Primary
    public PasswordEncoder fakePasswordEncoder() {
        return new FakePasswordEncoder();
    }
}
