package com.loopers.interfaces.api.example;

import com.loopers.application.example.ExampleInfo;

import java.util.UUID;

public class ExampleV1Dto {
    public record ExampleResponse(UUID id, String name, String description) {
        public static ExampleResponse from(ExampleInfo info) {
            return new ExampleResponse(
                info.id(),
                info.name(),
                info.description()
            );
        }
    }
}
