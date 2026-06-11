package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandApplicationService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public BrandInfo register(BrandCriteria.Register command) {
        Brand brand = Brand.create(command.name(), command.description());
        Brand saved = brandRepository.save(brand);
        return BrandInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long id) {
        Brand brand = brandRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public PageResult<BrandInfo> getBrandPage(int page, int size) {
        return brandRepository.findAll(page, size).map(BrandInfo::from);
    }

    @Transactional
    public void modify(BrandCriteria.Modify command) {
        Brand brand = brandRepository.find(command.id())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        brand.modify(command.name(), command.description());
        brandRepository.update(brand);
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = brandRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        brand.delete();
        brandRepository.update(brand);
        // 소속 상품과 그 재고를 함께 소프트 삭제. 재고 id 는 상품 소프트 삭제 '전에' 모아야 한다
        // (삭제 후엔 deleted_at IS NULL 조회로 잡히지 않음). 재고 cascade 로 "삭제 상품 주문" 경쟁도 함께 차단된다.
        List<Long> productIds = productRepository.findIdsByBrandId(id);
        productRepository.bulkSoftDeleteByBrandId(id);
        inventoryRepository.bulkSoftDeleteByProductIds(productIds);
    }
}
