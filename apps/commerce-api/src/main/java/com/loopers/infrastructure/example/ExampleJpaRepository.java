package com.loopers.infrastructure.example;

import com.loopers.domain.example.Example;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExampleJpaRepository extends JpaRepository<Example, Long> {}
