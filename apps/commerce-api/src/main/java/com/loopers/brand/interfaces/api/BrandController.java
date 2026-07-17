package com.loopers.brand.interfaces.api;

import com.loopers.brand.application.BrandFacade;
import com.loopers.brand.application.BrandInfo;
import com.loopers.common.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/brands")
public class BrandController {

    private final BrandFacade brandFacade;

    @GetMapping("/{brandId}")
    public ApiResponse<BrandResponse> getBrand(@PathVariable("brandId") Long brandId) {
        BrandInfo info = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandResponse.from(info));
    }
}
