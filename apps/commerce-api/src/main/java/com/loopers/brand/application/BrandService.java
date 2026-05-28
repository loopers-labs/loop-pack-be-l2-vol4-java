package com.loopers.brand.application;

import com.loopers.brand.domain.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;
    private final BrandReader brandReader;

    @Transactional(readOnly = true)
    public BrandResult.Detail get(Long brandId) {
        return BrandResult.Detail.from(brandReader.get(brandId));
    }

    @Transactional(readOnly = true)
    public List<BrandResult.Detail> getAll() {
        return brandRepository.findAll().stream()
                .map(BrandResult.Detail::from)
                .toList();
    }
}
