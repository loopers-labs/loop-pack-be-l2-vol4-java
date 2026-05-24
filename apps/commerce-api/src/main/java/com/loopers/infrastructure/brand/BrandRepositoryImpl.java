package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

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
    public PageResult<Brand> findActiveAll(PageQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size(), Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
        ));
        Page<Brand> page = brandJpaRepository.findByDeletedAtIsNull(pageable);
        return new PageResult<>(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize(),
            page.isFirst(),
            page.isLast()
        );
    }
}
