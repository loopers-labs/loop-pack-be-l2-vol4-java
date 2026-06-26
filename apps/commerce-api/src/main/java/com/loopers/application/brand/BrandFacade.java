package com.loopers.application.brand;

import com.loopers.application.like.LikeService;
import com.loopers.application.product.ProductCacheService;
import com.loopers.application.product.ProductService;
import com.loopers.application.stock.StockService;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;
    private final StockService stockService;
    private final LikeService likeService;
    private final ProductCacheService productCacheService;

    public BrandInfo create(String name) {
        BrandModel saved = brandService.create(new BrandModel(name));
        return BrandInfo.from(saved);
    }

    public BrandInfo getById(Long id) {
        return BrandInfo.from(brandService.getById(id));
    }

    public Page<BrandInfo> getAll(PageRequest pageRequest) {
        return brandService.getAll(pageRequest).map(BrandInfo::from);
    }

    public BrandInfo update(Long id, String name) {
        return BrandInfo.from(brandService.update(id, name));
    }

    @Transactional
    public void delete(Long id) {
        // 연결된 상품 → 재고 Soft Delete + 좋아요 Hard Delete (SD-04)
        productService.findAllByBrandId(id).forEach(product -> {
            product.delete();
            Optional<StockModel> stock = stockService.findByProductId(product.getId());
            stock.ifPresent(StockModel::delete);
            likeService.deleteAllByProductId(product.getId());
            productCacheService.evictDetail(product.getId());
        });
        productCacheService.evictAllList();
        brandService.delete(id);
    }
}
