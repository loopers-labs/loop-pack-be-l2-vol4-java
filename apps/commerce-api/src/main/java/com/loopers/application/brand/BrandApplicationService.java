package com.loopers.application.brand;

import com.loopers.domain.brand.BrandEntity;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BrandApplicationService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final LikeRepository likeRepository;

    public BrandInfo getBrand(String brandId) {
        BrandEntity brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        return BrandInfo.from(brand);
    }

    public Page<BrandInfo> getBrands(Pageable pageable) {
        return brandRepository.findAll(pageable).map(BrandInfo::from);
    }

    public BrandInfo createBrand(String name, String description) {
        brandRepository.findByName(name).ifPresent(b -> {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        });
        return BrandInfo.from(brandRepository.save(new BrandEntity(name, description)));
    }

    public BrandInfo updateBrand(String brandId, String name, String description) {
        BrandEntity brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

        brandRepository.findByName(name)
                .filter(found -> !found.getId().equals(brandId))
                .ifPresent(found -> {
                    throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
                });

        brand.update(name, description);
        return BrandInfo.from(brandRepository.save(brand));
    }

    @Transactional
    public void deleteBrand(String brandId) {
        BrandEntity brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        brand.delete();
        brandRepository.save(brand);

        List<String> productIds = productRepository.findIdsByBrandId(brandId);
        productRepository.findAllByIds(productIds).forEach(product -> {
            product.delete();
            productRepository.save(product);
        });
        inventoryRepository.deleteAllByProductIds(productIds);
        likeRepository.deleteAllByProductIds(productIds);
    }
}
