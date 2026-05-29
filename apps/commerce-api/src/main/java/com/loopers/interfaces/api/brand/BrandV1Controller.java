package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/brands")
public class BrandV1Controller {

    private final BrandApplicationService brandApplicationService;

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(@PathVariable @Min(1) Long brandId) {
        BrandInfo info = brandApplicationService.get(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }
}
