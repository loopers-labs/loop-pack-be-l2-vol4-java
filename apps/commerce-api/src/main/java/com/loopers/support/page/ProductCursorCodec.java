package com.loopers.support.page;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.product.ProductCursor;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * 불투명(opaque) 커서 코덱 — {@link ProductCursor} ↔ Base64URL(JSON) 문자열.
 *
 * <p>클라이언트에는 내부 정렬값/id를 노출하지 않는 불투명 토큰으로만 오간다. 직렬화는 주입된 Jackson
 * {@link ObjectMapper}로 처리하고(키 길이를 줄이려 {@code s/v/id}로 축약), 깨지거나 위조된 커서는
 * {@code BAD_REQUEST}로 막는다. Base64URL(패딩 없음)이라 쿼리스트링에 그대로 실어도 안전하다.
 */
@Component
@RequiredArgsConstructor
public class ProductCursorCodec {

    private final ObjectMapper objectMapper;

    public String encode(ProductCursor cursor) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(
                    new Payload(cursor.sort(), cursor.sortValue(), cursor.id()));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "커서 직렬화에 실패했습니다.");
        }
    }

    public ProductCursor decode(String encoded) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(encoded);
            Payload payload = objectMapper.readValue(json, Payload.class);
            return new ProductCursor(payload.sort(), payload.value(), payload.id());
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "잘못된 커서입니다.");
        }
    }

    private record Payload(
            @JsonProperty("s") ProductSortType sort,
            @JsonProperty("v") Long value,
            @JsonProperty("id") Long id
    ) {}
}
