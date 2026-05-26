package com.loopers.example.domain;

import java.util.Optional;

public interface ExampleRepository {
    Optional<ExampleModel> find(Long id);
}
