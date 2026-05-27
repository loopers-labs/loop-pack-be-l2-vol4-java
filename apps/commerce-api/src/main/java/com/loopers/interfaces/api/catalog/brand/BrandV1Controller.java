package com.loopers.interfaces.api.catalog.brand;

import com.loopers.application.catalog.brand.BrandQueryService;
import com.loopers.application.catalog.brand.BrandResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/brands")
public class BrandV1Controller {

    private final BrandQueryService brandQueryService;

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandResult result = brandQueryService.getActiveBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(result));
    }
}
