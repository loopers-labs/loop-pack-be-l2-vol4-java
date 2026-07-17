package com.loopers.example.interfaces.api;

import com.loopers.common.interfaces.api.ApiResponse;
import com.loopers.example.application.ExampleFacade;
import com.loopers.example.application.ExampleInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/examples")
public class ExampleController implements ExampleV1ApiSpec {

    private final ExampleFacade exampleFacade;

    @GetMapping("/{exampleId}")
    @Override
    public ApiResponse<ExampleResponse> getExample(
        @PathVariable(value = "exampleId") Long exampleId
    ) {
        ExampleInfo info = exampleFacade.getExample(exampleId);
        ExampleResponse response = ExampleResponse.from(info);
        return ApiResponse.success(response);
    }
}
