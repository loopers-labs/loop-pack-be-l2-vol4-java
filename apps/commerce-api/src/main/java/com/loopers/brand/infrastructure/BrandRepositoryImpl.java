package com.loopers.brand.infrastructure;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
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
    public Brand save(Brand brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public Optional<Brand> findActiveById(Long brandId) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public List<Brand> findActiveAllByIds(Collection<Long> brandIds) {
        if (brandIds.isEmpty()) {
            return List.of();
        }
        return brandJpaRepository.findByIdInAndDeletedAtIsNull(brandIds);
    }

    @Override
    public PageResult<Brand> findActiveAll(PageQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
        ));
        Page<Brand> page = brandJpaRepository.findByDeletedAtIsNull(pageable);
        return PageResult.from(page);
    }
}
