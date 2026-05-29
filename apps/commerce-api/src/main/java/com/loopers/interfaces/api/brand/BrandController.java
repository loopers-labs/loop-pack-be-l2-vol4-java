package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/brands")
public class BrandController {

    private final BrandFacade brandFacade;

    @GetMapping
    public ApiResponse<List<BrandDto.BrandResponse>> getBrands() {
        List<BrandInfo> brands = brandFacade.getAllBrands();
        return ApiResponse.success(brands.stream().map(BrandDto.BrandResponse::from).toList());
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandInfo brand = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }
}
