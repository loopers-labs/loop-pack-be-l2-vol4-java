package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller {

    private final BrandService brandService;

    /** FR-BA-01. 브랜드 목록 조회 */
    @GetMapping
    public ApiResponse<Page<BrandV1Dto.BrandResponse>> getBrands(
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(brandService.getAll(pageable).map(BrandV1Dto.BrandResponse::from));
    }

    /** FR-BA-01. 브랜드 상세 조회 */
    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(@PathVariable Long brandId) {
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brandService.getById(brandId)));
    }

    /** FR-BA-02. 브랜드 등록 */
    @PostMapping
    public ApiResponse<BrandV1Dto.BrandResponse> createBrand(
        @Valid @RequestBody BrandV1Dto.BrandCreateRequest request
    ) {
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brandService.create(request.toCommand())));
    }

    /** FR-BA-03. 브랜드 수정 */
    @PutMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> updateBrand(
        @PathVariable Long brandId,
        @Valid @RequestBody BrandV1Dto.BrandUpdateRequest request
    ) {
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brandService.update(brandId, request.toCommand())));
    }

    /** FR-BA-04. 브랜드 삭제 */
    @DeleteMapping("/{brandId}")
    public ApiResponse<Object> deleteBrand(@PathVariable Long brandId) {
        brandService.delete(brandId);
        return ApiResponse.success();
    }
}
