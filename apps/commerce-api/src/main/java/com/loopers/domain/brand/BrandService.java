package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public BrandModel register(String name, String description) {
        return brandRepository.save(new BrandModel(name, description));
    }

    /**
     * 대고객 조회: 활성 브랜드만 반환. 존재하지 않거나 비활성이면 NOT_FOUND로 통일 응대 (01 §7.4 정보 노출 방지).
     */
    @Transactional(readOnly = true)
    public BrandModel getActiveBrand(Long id) {
        return brandRepository.find(id)
                .filter(BrandModel::isActive)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }
}
