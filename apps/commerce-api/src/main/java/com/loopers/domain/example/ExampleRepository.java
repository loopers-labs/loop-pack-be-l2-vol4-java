package com.loopers.domain.example;

import java.util.Optional;
import java.util.UUID;

public interface ExampleRepository {
    Optional<ExampleModel> find(UUID id);
}
