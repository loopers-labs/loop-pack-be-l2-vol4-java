package com.loopers.domain.brand.service;

import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class BrandDomainService {

    private final BrandRepository brandRepository;

    public void validateDuplicateName(String name) {
        if (brandRepository.existsByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        }
    }
}
