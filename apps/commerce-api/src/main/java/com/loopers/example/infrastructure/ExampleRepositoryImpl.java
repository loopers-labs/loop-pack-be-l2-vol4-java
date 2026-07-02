package com.loopers.example.infrastructure;

import com.loopers.example.domain.Example;
import com.loopers.example.domain.ExampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ExampleRepositoryImpl implements ExampleRepository {
    private final ExampleJpaRepository exampleJpaRepository;

    @Override
    public Optional<Example> find(Long id) {
        return exampleJpaRepository.findById(id);
    }
}
