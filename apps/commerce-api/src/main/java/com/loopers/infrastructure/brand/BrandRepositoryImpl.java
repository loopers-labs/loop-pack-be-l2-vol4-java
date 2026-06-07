package com.loopers.infrastructure.brand;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public BrandModel save(BrandModel brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public boolean existsActiveByName(String name) {
        return brandJpaRepository.existsByNameValueAndDeletedAtIsNull(name);
    }

    @Override
    public boolean existsActiveByNameAndIdNot(String name, Long id) {
        return brandJpaRepository.existsByNameValueAndDeletedAtIsNullAndIdNot(name, id);
    }

    @Override
    public boolean existsActiveById(Long id) {
        return brandJpaRepository.existsByIdAndDeletedAtIsNull(id);
    }

    @Override
    public BrandModel getActiveById(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드가 존재하지 않습니다."));
    }

    @Override
    public Optional<BrandModel> findActiveById(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<BrandModel> findActiveByPage(int page, int size) {
        return brandJpaRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(PageRequest.of(page, size));
    }
}
