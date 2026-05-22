package com.loopers.util;

public final class PasswordRotateUtil {
    private PasswordRotateUtil() {}

    public static String rotateDigits(String password) {
        StringBuilder sb = new StringBuilder();
        for (char c : password.toCharArray()) {
            sb.append(Character.isDigit(c) ? (char) ('0' + (c - '0' + 1) % 10) : c);
        }
        return sb.toString();
    }
}
