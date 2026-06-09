package com.loopers.example.interfaces.api;

import com.loopers.example.application.ExampleInfo;

public record ExampleResponse(Long id, String name, String description) {
    public static ExampleResponse from(ExampleInfo info) {
        return new ExampleResponse(
            info.id(),
            info.name(),
            info.description()
        );
    }
}
