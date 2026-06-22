package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * likes_desc 키셋 커서. 마지막으로 본 행의 (like_count, product_id) 를 토큰으로 인코딩한다.
 */
public record LikesCursor(long likeCount, long productId) {

    public String encode() {
        String raw = likeCount + ":" + productId;
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** null/blank 토큰은 첫 페이지를 의미해 null 을 반환한다. 형식 오류는 BAD_REQUEST. */
    public static LikesCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("커서 형식 오류");
            }
            return new LikesCursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "잘못된 커서입니다.");
        }
    }
}
