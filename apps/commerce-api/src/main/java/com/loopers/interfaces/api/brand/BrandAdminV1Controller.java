package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/brands")
public class BrandAdminV1Controller implements BrandAdminV1ApiSpec {

    private final BrandFacade brandFacade;

    @PostMapping
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> create(@RequestBody @Valid BrandV1Dto.CreateRequest request) {
        BrandInfo info = brandFacade.create(request.name(), request.description());
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @GetMapping("/{id}")
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> get(@PathVariable UUID id) {
        BrandInfo info = brandFacade.get(id);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<Page<BrandV1Dto.BrandResponse>> getList(Pageable pageable) {
        Page<BrandInfo> page = brandFacade.getList(pageable);
        return ApiResponse.success(page.map(BrandV1Dto.BrandResponse::from));
    }

    @PutMapping("/{id}")
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> update(
        @PathVariable UUID id,
        @RequestBody @Valid BrandV1Dto.UpdateRequest request
    ) {
        BrandInfo info = brandFacade.update(id, request.name(), request.description());
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{id}")
    @Override
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        brandFacade.delete(id);
        return ApiResponse.success(null);
    }
}
