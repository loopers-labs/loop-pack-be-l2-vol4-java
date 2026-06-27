package com.loopers.infrastructure.pg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;

import java.io.IOException;
import java.lang.reflect.Type;

class PgDataUnwrappingDecoder implements Decoder {

    private final ObjectMapper objectMapper;

    PgDataUnwrappingDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, DecodeException, FeignException {
        if (response.body() == null) {
            return null;
        }
        JsonNode root = objectMapper.readTree(response.body().asInputStream());
        JsonNode data = root.path("data");
        return objectMapper.convertValue(data, objectMapper.constructType(type));
    }
}
