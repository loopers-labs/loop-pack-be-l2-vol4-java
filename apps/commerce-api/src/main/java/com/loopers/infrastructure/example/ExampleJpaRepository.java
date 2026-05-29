package com.loopers.infrastructure.example;

import com.loopers.domain.example.ExampleModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExampleJpaRepository extends JpaRepository<ExampleModel, UUID> {}
