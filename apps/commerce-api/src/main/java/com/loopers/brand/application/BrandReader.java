package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class BrandReader {

    private final BrandRepository brandRepository;

    public Brand get(Long brandId) {
        return brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
    }

    public Map<Long, String> getNames(List<Long> brandIds) {
        return brandRepository.findAllByIdIn(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Brand::getName));
    }
}
