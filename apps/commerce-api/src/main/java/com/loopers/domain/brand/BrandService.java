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

    /**
     * 브랜드 이름·설명 수정 (UC-10 Admin). 활성 브랜드만 수정 가능 — 비활성/부재면 NOT_FOUND.
     */
    @Transactional
    public BrandModel update(Long id, String name, String description) {
        BrandModel brand = getActiveBrand(id);
        brand.update(name, description);
        return brandRepository.save(brand);
    }

    /**
     * 브랜드 soft delete (01 §7.5). 존재하지 않으면 NOT_FOUND. 멱등 — 이미 비활성이어도 안전.
     * 하위 상품/좋아요 cascade 전파는 BrandFacade가 조정한다.
     */
    @Transactional
    public void deleteBrand(Long id) {
        BrandModel brand = brandRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
        brand.delete();
        brandRepository.save(brand);
    }
}
