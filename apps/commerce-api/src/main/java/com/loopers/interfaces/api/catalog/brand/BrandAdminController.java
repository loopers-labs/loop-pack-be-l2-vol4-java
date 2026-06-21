package com.loopers.interfaces.api.catalog.brand;

import com.loopers.application.catalog.brand.BrandCommand;
import com.loopers.application.catalog.brand.BrandCommandService;
import com.loopers.application.catalog.brand.BrandQueryService;
import com.loopers.application.catalog.brand.BrandResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminController {

    private final BrandCommandService brandCommandService;
    private final BrandQueryService brandQueryService;

    @GetMapping
    public ApiResponse<PageResponse<BrandAdminDto.BrandResponse>> getBrands(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        HeaderValidator.validateAdmin(ldap);
        PageResult<BrandResult> result = brandQueryService.getAdminBrands(page, size);
        return ApiResponse.success(PageResponse.from(result, BrandAdminDto.BrandResponse::from));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandAdminDto.BrandResponse> getBrand(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @PathVariable Long brandId
    ) {
        HeaderValidator.validateAdmin(ldap);
        BrandResult result = brandQueryService.getAdminBrand(brandId);
        return ApiResponse.success(BrandAdminDto.BrandResponse.from(result));
    }

    @PostMapping
    public ApiResponse<BrandAdminDto.BrandResponse> createBrand(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @RequestBody BrandAdminDto.CreateBrandRequest request
    ) {
        HeaderValidator.validateAdmin(ldap);
        if (request == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 등록 요청은 필수입니다.");
        }
        BrandResult result = brandCommandService.create(new BrandCommand.Create(
            request.name(),
            request.description()
        ));

        return ApiResponse.success(BrandAdminDto.BrandResponse.from(result));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandAdminDto.BrandResponse> updateBrand(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @PathVariable Long brandId,
        @RequestBody BrandAdminDto.UpdateBrandRequest request
    ) {
        HeaderValidator.validateAdmin(ldap);
        if (request == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 수정 요청은 필수입니다.");
        }
        BrandResult result = brandCommandService.update(
            brandId,
            new BrandCommand.Update(request.name(), request.description())
        );

        return ApiResponse.success(BrandAdminDto.BrandResponse.from(result));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @PathVariable Long brandId
    ) {
        HeaderValidator.validateAdmin(ldap);
        brandCommandService.delete(brandId);
        return ApiResponse.success(null);
    }
}
