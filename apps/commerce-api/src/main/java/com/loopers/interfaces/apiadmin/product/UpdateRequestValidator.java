package com.loopers.interfaces.apiadmin.product;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class UpdateRequestValidator implements ConstraintValidator<ValidUpdateRequest, ProductAdminV1Dto.UpdateRequest> {

    @Override
    public boolean isValid(ProductAdminV1Dto.UpdateRequest request, ConstraintValidatorContext context) {
        return request.name() != null || request.stockId() != null;
    }
}
