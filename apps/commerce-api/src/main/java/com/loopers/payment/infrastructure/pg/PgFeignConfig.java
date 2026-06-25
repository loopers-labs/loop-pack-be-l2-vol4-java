package com.loopers.payment.infrastructure.pg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class PgFeignConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return new PgErrorDecoder();
    }

    // PG 응답은 {"meta":..., "data":{...}} 래퍼 구조 — data 필드만 꺼내 DTO로 역직렬화
    @Bean
    public Decoder decoder(ObjectMapper objectMapper) {
        return (response, type) -> {
            JsonNode root = objectMapper.readTree(response.body().asInputStream());
            JsonNode data = root.get("data");
            return objectMapper.treeToValue(data, objectMapper.constructType(type));
        };
    }
}
