package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandAdminInfo;
import com.loopers.application.brand.BrandFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/brands")
public class BrandAdminV1Controller implements BrandAdminV1ApiSpec {

    private final BrandFacade brandFacade;

    @PostMapping
    @Override
    public ApiResponse<BrandAdminV1Dto.Response> create(
        @RequestBody BrandAdminV1Dto.CreateRequest request
    ) {
        BrandAdminInfo info = brandFacade.create(request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.Response.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<BrandAdminV1Dto.PageResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<BrandAdminInfo> result = brandFacade.list(PageRequest.of(page, size));
        return ApiResponse.success(BrandAdminV1Dto.PageResponse.from(result));
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandAdminV1Dto.Response> getBrand(
        @PathVariable(value = "brandId") Long brandId
    ) {
        BrandAdminInfo info = brandFacade.getForAdmin(brandId);
        return ApiResponse.success(BrandAdminV1Dto.Response.from(info));
    }

    @PatchMapping("/{brandId}")
    @Override
    public ApiResponse<BrandAdminV1Dto.Response> update(
        @PathVariable(value = "brandId") Long brandId,
        @RequestBody BrandAdminV1Dto.UpdateRequest request
    ) {
        BrandAdminInfo info = brandFacade.update(brandId, request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.Response.from(info));
    }
}
