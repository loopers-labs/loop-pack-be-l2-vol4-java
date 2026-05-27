package com.loopers.application.brand;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class BrandFacade {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public BrandCreateInfo createBrand(String name, String description) {
        if (brandRepository.existsActiveByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 브랜드 이름입니다.");
        }

        BrandModel newBrand = BrandModel.builder()
            .rawName(name)
            .rawDescription(description)
            .build();

        return BrandCreateInfo.from(brandRepository.save(newBrand));
    }

    public BrandUpdateInfo updateBrand(Long brandId, String name, String description) {
        BrandModel brand = brandRepository.getActiveById(brandId);

        if (brandRepository.existsActiveByNameAndIdNot(name, brandId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용 중인 브랜드 이름입니다.");
        }

        brand.update(name, description);

        return BrandUpdateInfo.from(brand);
    }

    public void deleteBrand(Long brandId) {
        brandRepository.findActiveById(brandId)
            .ifPresent(brand -> {
                brand.delete();
                productRepository.findActiveByBrandId(brandId).forEach(ProductModel::delete);
            });
    }

    @Transactional(readOnly = true)
    public BrandInfo readBrand(Long brandId) {
        BrandModel brand = brandRepository.getActiveById(brandId);

        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public Page<BrandInfo> readBrands(int page, int size) {
        Page<BrandModel> brandModels = brandRepository.findActiveByPage(page, size);

        return brandModels.map(BrandInfo::from);
    }
}
