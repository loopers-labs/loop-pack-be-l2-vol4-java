package com.loopers.brand.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BrandService {

    public BrandModel getOrThrow(Optional<BrandModel> brand) {
        return brand.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드가 존재하지 않습니다."));
    }
}
