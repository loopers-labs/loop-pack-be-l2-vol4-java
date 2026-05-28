package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandCommand;
import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller {

    private final BrandService brandService;
    private final BrandFacade brandFacade;

    @GetMapping
    public ApiResponse<Page<BrandAdminV1Dto.BrandResponse>> getAllBrands(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<BrandInfo> brands = brandService.getAllBrands(PageRequest.of(page, size))
            .map(BrandInfo::from);
        return ApiResponse.success(brands.map(BrandAdminV1Dto.BrandResponse::from));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandInfo info = BrandInfo.from(brandService.getBrand(brandId));
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @PostMapping
    public ApiResponse<BrandAdminV1Dto.BrandResponse> createBrand(
        @RequestBody @Valid BrandAdminV1Dto.BrandCreateRequest request
    ) {
        BrandInfo info = brandFacade.createBrand(new BrandCommand.Create(request.name(), request.description()));
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> updateBrand(
        @PathVariable Long brandId,
        @RequestBody @Valid BrandAdminV1Dto.BrandUpdateRequest request
    ) {
        BrandInfo info = brandFacade.updateBrand(new BrandCommand.Update(brandId, request.name(), request.description()));
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(@PathVariable Long brandId) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }
}
