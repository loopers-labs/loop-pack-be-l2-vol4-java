package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class BrandController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final BrandFacade brandFacade;

    @GetMapping("/api/v1/brands/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandInfo info = brandFacade.getById(brandId);
        return ApiResponse.success(BrandDto.BrandResponse.from(info));
    }

    @GetMapping("/api-admin/v1/brands/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> getBrandAdmin(@PathVariable Long brandId) {
        BrandInfo info = brandFacade.getById(brandId);
        return ApiResponse.success(BrandDto.BrandResponse.from(info));
    }

    @GetMapping("/api-admin/v1/brands")
    public ApiResponse<BrandDto.BrandPageResponse> getBrands(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        Page<BrandInfo> result = brandFacade.getAll(PageRequest.of(page, size));
        BrandDto.BrandPageResponse body = new BrandDto.BrandPageResponse(
            result.getContent().stream().map(BrandDto.BrandResponse::from).toList(),
            result.getTotalElements(),
            result.getTotalPages()
        );
        return ApiResponse.success(body);
    }

    @PostMapping("/api-admin/v1/brands")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandDto.BrandResponse> createBrand(@Valid @RequestBody BrandDto.CreateRequest request) {
        BrandInfo info = brandFacade.create(request.name());
        return ApiResponse.success(BrandDto.BrandResponse.from(info));
    }

    @PutMapping("/api-admin/v1/brands/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> updateBrand(
        @PathVariable Long brandId,
        @Valid @RequestBody BrandDto.UpdateRequest request
    ) {
        BrandInfo info = brandFacade.update(brandId, request.name());
        return ApiResponse.success(BrandDto.BrandResponse.from(info));
    }

    @DeleteMapping("/api-admin/v1/brands/{brandId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBrand(@PathVariable Long brandId) {
        brandFacade.delete(brandId);
    }
}
