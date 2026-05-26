package com.loopers.interfaces.api.admin;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class AdminBrandV1Controller {

    private final BrandFacade brandFacade;

    @GetMapping
    public ApiResponse<List<BrandV1Dto.BrandResponse>> getBrands(
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size
    ) {
        List<BrandV1Dto.BrandResponse> responses = brandFacade.getBrands(page, size).stream()
            .map(BrandV1Dto.BrandResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(
        @PathVariable(value = "brandId") Long brandId
    ) {
        BrandInfo info = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @PostMapping
    public ApiResponse<BrandV1Dto.BrandResponse> createBrand(
        @Valid @RequestBody BrandV1Dto.CreateBrandRequest request
    ) {
        BrandInfo info = brandFacade.createBrand(request.name(), request.description());
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> updateBrand(
        @PathVariable(value = "brandId") Long brandId,
        @Valid @RequestBody BrandV1Dto.UpdateBrandRequest request
    ) {
        BrandInfo info = brandFacade.updateBrand(brandId, request.name(), request.description());
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(
        @PathVariable(value = "brandId") Long brandId
    ) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }
}
