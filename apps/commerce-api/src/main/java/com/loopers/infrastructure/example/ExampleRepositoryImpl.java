package com.loopers.infrastructure.example;

import com.loopers.domain.example.ExampleModel;
import com.loopers.domain.example.ExampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class ExampleRepositoryImpl implements ExampleRepository {
    private final ExampleJpaRepository exampleJpaRepository;

    @Override
    public Optional<ExampleModel> find(UUID id) {
        return exampleJpaRepository.findById(id);
    }
}
