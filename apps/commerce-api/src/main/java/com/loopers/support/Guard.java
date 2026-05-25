package com.loopers.support;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public final class Guard {

    private Guard() {}

    public static void notBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public static void notNull(Object value, String message) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public static void minLength(String value, int min, String message) {
        if (value == null || value.length() < min) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public static void notNegative(Long value, String message) {
        if (value == null || value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public static void positive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public static void notNegative(Integer value, String message) {
        if (value == null || value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public static void maxLength(String value, int max, String message) {
        if (value != null && value.length() > max) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }

    public static void matches(String value, java.util.regex.Pattern pattern, String message) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }
    }
}
