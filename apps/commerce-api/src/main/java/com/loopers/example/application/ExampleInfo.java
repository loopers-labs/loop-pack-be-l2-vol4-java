package com.loopers.example.application;

import com.loopers.example.domain.Example;

public record ExampleInfo(Long id, String name, String description) {
    public static ExampleInfo from(Example model) {
        return new ExampleInfo(
            model.getId(),
            model.getName(),
            model.getDescription()
        );
    }
}
