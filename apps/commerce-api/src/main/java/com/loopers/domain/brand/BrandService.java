package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public BrandModel getBrand(Long id) {
        return brandRepository.find(id)
                .orElseThrow(
                    () -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
                );
    }

    @Transactional(readOnly = true)
    public Page<BrandModel> getBrands(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name.value").ascending());
        if (keyword == null) {
            return brandRepository.findAllNotDeleted(pageable);
        }
        return brandRepository.findByNameContainingAndNotDeleted(keyword, pageable);
    }
}