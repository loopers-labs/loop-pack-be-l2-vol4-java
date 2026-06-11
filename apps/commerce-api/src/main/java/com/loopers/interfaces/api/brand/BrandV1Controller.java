package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
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

    private final BrandFacade brandFacade;

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandInfo info = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }
}
