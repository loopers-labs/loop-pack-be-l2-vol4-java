package com.loopers.example.application;

import com.loopers.example.domain.Example;
import com.loopers.example.domain.ExampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ExampleFacade {
    private final ExampleService exampleService;

    public ExampleInfo getExample(Long id) {
        Example example = exampleService.getExample(id);
        return ExampleInfo.from(example);
    }
}
