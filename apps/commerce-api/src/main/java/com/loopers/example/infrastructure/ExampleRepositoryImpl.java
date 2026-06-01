package com.loopers.example.infrastructure;

import com.loopers.example.domain.ExampleModel;
import com.loopers.example.domain.ExampleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ExampleRepositoryImpl implements ExampleRepository {
    private final ExampleJpaRepository exampleJpaRepository;

    @Override
    public Optional<ExampleModel> find(Long id) {
        return exampleJpaRepository.findById(id);
    }
}
