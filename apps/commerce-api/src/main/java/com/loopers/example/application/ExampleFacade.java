package com.loopers.example.application;

import com.loopers.example.domain.ExampleModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional
public class ExampleFacade {
    private final ExampleService exampleService;

    @Transactional(readOnly = true)
    public ExampleInfo getExample(Long id) {
        ExampleModel example = exampleService.getExample(id);
        return ExampleInfo.from(example);
    }
}
