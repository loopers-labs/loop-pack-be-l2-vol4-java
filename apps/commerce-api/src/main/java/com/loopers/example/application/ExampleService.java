package com.loopers.example.application;

import com.loopers.example.domain.Example;
import com.loopers.example.domain.ExampleRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ExampleService {

    private final ExampleRepository exampleRepository;

    public Example getExample(Long id) {
        return exampleRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 예시를 찾을 수 없습니다."));
    }
}
