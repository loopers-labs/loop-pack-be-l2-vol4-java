package com.loopers.example.infrastructure;

import com.loopers.example.domain.Example;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExampleJpaRepository extends JpaRepository<Example, Long> {}
