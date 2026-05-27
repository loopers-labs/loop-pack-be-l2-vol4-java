package com.loopers.brand.interfaces.api;

import com.loopers.brand.application.BrandService;
import com.loopers.brand.domain.Brand;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/brands")
public class BrandV1Controller implements BrandV1ApiSpec {

    private final BrandService brandService;

    @PostMapping
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> create(
        @Valid @RequestBody BrandV1Dto.CreateRequest request
    ) {
        Brand brand = brandService.create(request.toCommand());
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brand));
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> get(@PathVariable Long brandId) {
        Brand brand = brandService.get(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brand));
    }

    @GetMapping
    @Override
    public ApiResponse<List<BrandV1Dto.BrandResponse>> getAll() {
        List<BrandV1Dto.BrandResponse> responses = brandService.getAll().stream()
                .map(BrandV1Dto.BrandResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }

    @PutMapping("/{brandId}")
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> update(
        @PathVariable Long brandId,
        @Valid @RequestBody BrandV1Dto.UpdateRequest request
    ) {
        Brand brand = brandService.update(request.toCommand(brandId));
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brand));
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long brandId) {
        brandService.delete(brandId);
        return ApiResponse.success(null);
    }
}
