package com.loopers.brand.interfaces.api;

import com.loopers.brand.application.BrandAdminService;
import com.loopers.brand.application.BrandResult;
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
@RequestMapping("/api/v1/admin/brands")
public class BrandAdminV1Controller implements BrandAdminV1ApiSpec {

    private final BrandAdminService brandAdminService;

    @PostMapping
    @Override
    public ApiResponse<BrandV1Response.Detail> create(
        @Valid @RequestBody BrandAdminV1Request.Create request
    ) {
        BrandResult.Detail result = brandAdminService.create(request.toCommand());
        return ApiResponse.success(BrandV1Response.Detail.from(result));
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandV1Response.Detail> get(@PathVariable Long brandId) {
        return ApiResponse.success(BrandV1Response.Detail.from(brandAdminService.getBrand(brandId)));
    }

    @GetMapping
    @Override
    public ApiResponse<List<BrandV1Response.Detail>> getAll() {
        List<BrandV1Response.Detail> responses = brandAdminService.getBrands().stream()
                .map(BrandV1Response.Detail::from)
                .toList();
        return ApiResponse.success(responses);
    }

    @PutMapping("/{brandId}")
    @Override
    public ApiResponse<BrandV1Response.Detail> update(
        @PathVariable Long brandId,
        @Valid @RequestBody BrandAdminV1Request.Update request
    ) {
        BrandResult.Detail result = brandAdminService.update(request.toCommand(brandId));
        return ApiResponse.success(BrandV1Response.Detail.from(result));
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long brandId) {
        brandAdminService.delete(brandId);
        return ApiResponse.success(null);
    }
}
