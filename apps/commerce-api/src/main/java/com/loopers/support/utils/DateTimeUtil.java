package com.loopers.support.utils;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

@Component
public class DateTimeUtil {

    public ZonedDateTime now() {
        return ZonedDateTime.now();
    }
}
