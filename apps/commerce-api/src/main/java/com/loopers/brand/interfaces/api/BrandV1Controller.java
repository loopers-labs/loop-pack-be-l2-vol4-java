package com.loopers.brand.interfaces.api;

import com.loopers.brand.application.BrandQueryService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/brands")
public class BrandV1Controller implements BrandV1ApiSpec {

    private final BrandQueryService brandQueryService;

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandV1Response.Detail> get(@PathVariable Long brandId) {
        return ApiResponse.success(BrandV1Response.Detail.from(brandQueryService.getBrand(brandId)));
    }

    @GetMapping
    @Override
    public ApiResponse<List<BrandV1Response.Detail>> getAll() {
        List<BrandV1Response.Detail> responses = brandQueryService.getBrands().stream()
                .map(BrandV1Response.Detail::from)
                .toList();
        return ApiResponse.success(responses);
    }
}
