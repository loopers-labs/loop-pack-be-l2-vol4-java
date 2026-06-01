package com.loopers.example.application;

import com.loopers.example.domain.ExampleModel;
import com.loopers.example.domain.ExampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ExampleFacade {
    private final ExampleService exampleService;

    public ExampleInfo getExample(Long id) {
        ExampleModel example = exampleService.getExample(id);
        return ExampleInfo.from(example);
    }
}
