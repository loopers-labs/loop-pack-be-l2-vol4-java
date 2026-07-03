package com.loopers.support;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class NumberGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private NumberGenerator() {
    }

    public static String generate() {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String sequence = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        return timestamp + sequence;
    }
}
