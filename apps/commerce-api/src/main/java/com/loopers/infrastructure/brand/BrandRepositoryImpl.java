package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public BrandModel save(BrandModel brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public Optional<BrandModel> findById(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<BrandModel> findAllByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public Page<BrandModel> search(Pageable pageable) {
        return brandJpaRepository.findAllByDeletedAtIsNull(withIdDescTiebreak(pageable));
    }

    /** id DESC tiebreak으로 페이지 사이 중복/누락을 방지한다 (ProductRepositoryImpl.toSort와 같은 정책). */
    private Pageable withIdDescTiebreak(Pageable pageable) {
        Sort sort = pageable.getSort();
        Sort normalized = sort.getOrderFor("id") != null
            ? sort
            : sort.and(Sort.by(Sort.Order.desc("id")));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), normalized);
    }
}
