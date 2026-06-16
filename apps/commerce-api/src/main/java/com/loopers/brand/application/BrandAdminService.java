package com.loopers.brand.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.brand.domain.BrandErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandAdminService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;

    @Transactional
    public BrandResult.Detail create(BrandCommand.Create command) {
        if (brandRepository.existsByName(command.name())) {
            throw new CoreException(ErrorType.CONFLICT, BrandErrorCode.BRAND_NAME_DUPLICATED);
        }
        Brand brand = Brand.create(command.name(), command.description(), command.logoUrl());
        return BrandResult.Detail.from(brandRepository.save(brand));
    }

    @Transactional
    public BrandResult.Detail update(BrandCommand.Update command) {
        Brand brand = get(command.brandId());
        if (!brand.getName().equals(command.name()) && brandRepository.existsByName(command.name())) {
            throw new CoreException(ErrorType.CONFLICT, BrandErrorCode.BRAND_NAME_DUPLICATED);
        }
        brand.update(command.name(), command.description(), command.logoUrl());
        return BrandResult.Detail.from(brand);
    }

    @Transactional
    public void delete(Long brandId) {
        get(brandId);
        productStockRepository.softDeleteByBrandId(brandId);
        productRepository.softDeleteByBrandId(brandId);
        brandRepository.softDeleteById(brandId);
    }

    @Transactional(readOnly = true)
    public BrandResult.Detail getBrand(Long brandId) {
        return BrandResult.Detail.from(get(brandId));
    }

    @Transactional(readOnly = true)
    public List<BrandResult.Detail> getBrands() {
        return brandRepository.findAll().stream()
                .map(BrandResult.Detail::from)
                .toList();
    }

    private Brand get(Long brandId) {
        return brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, BrandErrorCode.BRAND_NOT_FOUND));
    }
}
