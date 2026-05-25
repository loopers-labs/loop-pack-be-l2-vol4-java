package com.loopers.application.brand;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class BrandFacade {

    private final BrandRepository brandRepository;

    public BrandCreateInfo createBrand(String name, String description) {
        if (brandRepository.existsActiveByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 브랜드 이름입니다.");
        }

        BrandModel newBrand = BrandModel.builder()
            .rawName(name)
            .rawDescription(description)
            .build();

        return BrandCreateInfo.from(brandRepository.save(newBrand));
    }
}
