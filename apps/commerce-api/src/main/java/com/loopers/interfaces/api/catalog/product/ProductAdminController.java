package com.loopers.interfaces.api.catalog.product;

import com.loopers.application.catalog.product.ProductCommand;
import com.loopers.application.catalog.product.ProductCommandService;
import com.loopers.application.catalog.product.ProductQuery;
import com.loopers.application.catalog.product.ProductQueryService;
import com.loopers.application.catalog.product.ProductResult;
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
@RequestMapping("/api-admin/v1/products")
public class ProductAdminController {

    private final ProductCommandService productCommandService;
    private final ProductQueryService productQueryService;

    @GetMapping
    public ApiResponse<PageResponse<ProductAdminDto.ProductResponse>> getProducts(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "latest") String sort
    ) {
        HeaderValidator.validateAdmin(ldap);
        PageResult<ProductResult> result = productQueryService.searchProducts(
            new ProductQuery.AdminSearch(brandId, page, size, sort)
        );
        return ApiResponse.success(PageResponse.from(result, ProductAdminDto.ProductResponse::from));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductAdminDto.ProductResponse> getProduct(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @PathVariable Long productId
    ) {
        HeaderValidator.validateAdmin(ldap);
        ProductResult result = productQueryService.getProduct(productId);
        return ApiResponse.success(ProductAdminDto.ProductResponse.from(result));
    }

    @PostMapping
    public ApiResponse<ProductAdminDto.ProductResponse> createProduct(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @RequestBody ProductAdminDto.CreateProductRequest request
    ) {
        HeaderValidator.validateAdmin(ldap);
        if (request == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 등록 요청은 필수입니다.");
        }
        ProductResult result = productCommandService.create(new ProductCommand.Create(
            request.brandId(),
            request.name(),
            request.description(),
            request.price(),
            request.stockQuantity(),
            request.status()
        ));

        return ApiResponse.success(ProductAdminDto.ProductResponse.from(result));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductAdminDto.ProductResponse> updateProduct(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @PathVariable Long productId,
        @RequestBody ProductAdminDto.UpdateProductRequest request
    ) {
        HeaderValidator.validateAdmin(ldap);
        if (request == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 수정 요청은 필수입니다.");
        }
        ProductResult result = productCommandService.update(
            productId,
            new ProductCommand.Update(
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity(),
                request.status()
            )
        );

        return ApiResponse.success(ProductAdminDto.ProductResponse.from(result));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
        @RequestHeader(HeaderValidator.ADMIN_LDAP) String ldap,
        @PathVariable Long productId
    ) {
        HeaderValidator.validateAdmin(ldap);
        productCommandService.stopSelling(productId);
        return ApiResponse.success(null);
    }
}
