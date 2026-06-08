package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller {

    private final BrandApplicationService brandApplicationService;

    @GetMapping
    public ApiResponse<Page<BrandAdminV1Dto.BrandResponse>> getBrands(
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 100, message = "size는 100 이하여야 합니다.") int size
    ) {
        Page<BrandInfo> brands = brandApplicationService.getAll(PageRequest.of(page, size));
        return ApiResponse.success(brands.map(BrandAdminV1Dto.BrandResponse::from));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> getBrand(@PathVariable @Min(1) Long brandId) {
        BrandInfo info = brandApplicationService.get(brandId);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @PostMapping
    public ApiResponse<BrandAdminV1Dto.BrandResponse> createBrand(
        @RequestBody @Valid BrandAdminV1Dto.CreateBrandRequest request
    ) {
        BrandInfo info = brandApplicationService.create(request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> updateBrand(
        @PathVariable @Min(1) Long brandId,
        @RequestBody @Valid BrandAdminV1Dto.UpdateBrandRequest request
    ) {
        BrandInfo info = brandApplicationService.update(brandId, request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(@PathVariable @Min(1) Long brandId) {
        brandApplicationService.delete(brandId);
        return ApiResponse.success(null);
    }
}
