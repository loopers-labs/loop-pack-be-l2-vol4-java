package com.loopers.interfaces.apiadmin.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller implements BrandAdminV1ApiSpec {

    private final BrandFacade brandFacade;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>> getList(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(brandFacade.getList(pageable).map(BrandAdminV1Dto.BrandResponse::from))
        );
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> getBrand(@PathVariable Long brandId) {
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(brandFacade.getBrand(brandId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> register(
            @Valid @RequestBody BrandAdminV1Dto.RegisterRequest request
    ) {
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(brandFacade.register(request.name())));
    }

    @PutMapping("/{brandId}")
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> update(
            @PathVariable Long brandId,
            @Valid @RequestBody BrandAdminV1Dto.UpdateRequest request
    ) {
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(brandFacade.update(brandId, request.name())));
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long brandId) {
        brandFacade.delete(brandId);
        return ApiResponse.success((Void) null);
    }
}
