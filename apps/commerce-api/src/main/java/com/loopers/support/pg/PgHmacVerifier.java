package com.loopers.support.pg;

import com.loopers.config.PgHmacProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * PG 콜백 HMAC-SHA256 서명 검증기.
 * PG → 서버 요청의 무결성(위변조 방지)을 검증한다.
 */
@Component
@RequiredArgsConstructor
public class PgHmacVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private final PgHmacProperties properties;

    /**
     * 서명 검증 — 불일치 시 UNAUTHORIZED 예외.
     * @param payload   raw request body 문자열
     * @param signature X-PG-Signature 헤더 값 (hex 인코딩)
     */
    public void verify(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "PG 서명이 없습니다.");
        }
        String expected = computeHmac(payload, properties.getHmacSecret());
        if (!MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "PG 서명이 일치하지 않습니다.");
        }
    }

    /** HMAC-SHA256(payload, secret) → hex 문자열 */
    public static String computeHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "HMAC 계산 중 오류가 발생했습니다.");
        }
    }
}
