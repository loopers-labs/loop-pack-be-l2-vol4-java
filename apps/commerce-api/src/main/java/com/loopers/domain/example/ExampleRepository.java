package com.loopers.domain.example;

import java.util.Optional;

public interface ExampleRepository {
    Optional<Example> find(Long id);
}
