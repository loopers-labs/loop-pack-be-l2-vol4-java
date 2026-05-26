package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    /**
     * 순수 도메인 ↔ JPA 엔티티 경계.
     * - 신규(id == null): 매퍼로 엔티티를 만들어 INSERT.
     * - 기존(id != null): managed 엔티티를 로드해 가변 상태만 복사 → dirty checking으로 UPDATE.
     *   soft delete 상태(deletedAt)도 도메인 기준으로 delete()/restore() 동기화한다(둘 다 멱등).
     *   (BaseEntity의 id가 final이라 도메인을 그대로 새 엔티티로 만들면 INSERT로 오인되므로 이 경로가 필요하다.)
     */
    @Override
    public BrandModel save(BrandModel brand) {
        if (brand.getId() == null) {
            BrandEntity saved = brandJpaRepository.save(BrandEntityMapper.toEntity(brand));
            return BrandEntityMapper.toDomain(saved);
        }
        BrandEntity entity = brandJpaRepository.findById(brand.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + brand.getId() + "] 브랜드를 찾을 수 없습니다."));
        entity.applyState(brand.getName(), brand.getDescription());
        if (brand.isActive()) {
            entity.restore();
        } else {
            entity.delete();
        }
        return BrandEntityMapper.toDomain(brandJpaRepository.save(entity));
    }

    @Override
    public Optional<BrandModel> find(Long id) {
        return brandJpaRepository.findById(id).map(BrandEntityMapper::toDomain);
    }
}
