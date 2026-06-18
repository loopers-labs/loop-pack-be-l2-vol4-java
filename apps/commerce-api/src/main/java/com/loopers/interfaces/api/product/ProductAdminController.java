package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductAdminFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api-admin/v1/products")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductAdminFacade productAdminFacade;

    @PostMapping
    public ApiResponse<Long> registerProduct(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @RequestBody ProductAdminDto.RegisterProductRequest request
    ) {
        validateAdmin(ldap);
        return ApiResponse.success(productAdminFacade.registerProduct(
                request.brandId(), request.name(), request.price(), request.initialStock()
        ));
    }

    @PutMapping("/{productId}")
    public ApiResponse<Void> updateProduct(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PathVariable Long productId,
            @RequestBody ProductAdminDto.UpdateProductRequest request
    ) {
        validateAdmin(ldap);
        productAdminFacade.updateProduct(productId, request.name(), request.price());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PathVariable Long productId
    ) {
        validateAdmin(ldap);
        productAdminFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }

    private void validateAdmin(String ldap) {
        if (!"loopers.admin".equals(ldap)) {
            throw new CoreException(ErrorType.NOT_FOUND, "권한이 없습니다.");
        }
    }
}
