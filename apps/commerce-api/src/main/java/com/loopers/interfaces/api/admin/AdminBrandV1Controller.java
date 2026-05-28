package com.loopers.interfaces.api.admin;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.BrandDto;
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
    public ApiResponse<List<BrandDto.List.V1.Response>> getBrands(
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size
    ) {
        List<BrandDto.List.V1.Response> responses = brandFacade.getBrands(page, size).stream()
            .map(BrandDto.List.V1.Response::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandDto.Get.V1.Response> getBrand(
        @PathVariable(value = "brandId") Long brandId
    ) {
        BrandInfo info = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandDto.Get.V1.Response.from(info));
    }

    @PostMapping
    public ApiResponse<BrandDto.Create.V1.Response> createBrand(
        @Valid @RequestBody BrandDto.Create.V1.Request request
    ) {
        BrandInfo info = brandFacade.createBrand(request.name(), request.description());
        return ApiResponse.success(BrandDto.Create.V1.Response.from(info));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandDto.Update.V1.Response> updateBrand(
        @PathVariable(value = "brandId") Long brandId,
        @Valid @RequestBody BrandDto.Update.V1.Request request
    ) {
        BrandInfo info = brandFacade.updateBrand(brandId, request.name(), request.description());
        return ApiResponse.success(BrandDto.Update.V1.Response.from(info));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(
        @PathVariable(value = "brandId") Long brandId
    ) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }
}
