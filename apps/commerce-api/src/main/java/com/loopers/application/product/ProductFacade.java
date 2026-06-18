package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.application.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductRepository;
import com.loopers.application.like.LikeRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final LikeRepository likeRepository;

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        ProductModel product = productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND, "[id = " + id + "] ?곹뭹??李얠쓣 ???놁뒿?덈떎."));
        BrandModel brand = brandRepository.findById(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        int likeCount = likeRepository.countByProductId(id);
        return ProductInfo.from(product, brand.getName(), likeCount);
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getProducts(Long brandId, String sort, Pageable pageable) {
        Page<ProductModel> productPage = productRepository.findAll(brandId, sort, pageable);

        List<Long> brandIds = productPage.getContent().stream()
                .map(ProductModel::getBrandId)
                .distinct()
                .toList();

        List<BrandModel> brands = brandRepository.findByIds(brandIds);
        Map<Long, String> brandNameMap = brands.stream()
                .collect(Collectors.toMap(BrandModel::getId, BrandModel::getName));

        List<Long> productIds = productPage.getContent().stream().map(ProductModel::getId).toList();
        Map<Long, Integer> fetchedLikeCounts = likeRepository.countByProductIds(productIds);

        return productPage.map(product -> {
            String brandName = brandNameMap.getOrDefault(product.getBrandId(), "?????놁쓬");
            int likeCount = fetchedLikeCounts.getOrDefault(product.getId(), 0);
                    
            return ProductInfo.from(product, brandName, likeCount);
        });
    }

    @Transactional
    public void decreaseStocks(List<StockRequest> requests) {
        for (StockRequest request : requests) {
            ProductModel product = productRepository.findById(request.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
            
            if (product.getStock() == null) {
                throw new CoreException(ErrorType.STOCK_NOT_FOUND);
            }
            
            product.getStock().decrease(request.quantity());
        }
    }

    @Transactional
    public void decreaseStocksWithLock(List<StockRequest> requests) {
        List<StockRequest> sortedRequests = requests.stream()
                .sorted(java.util.Comparator.comparing(StockRequest::productId))
                .toList();

        for (StockRequest request : sortedRequests) {
            ProductModel product = productRepository.findByIdWithLock(request.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
            
            if (product.getStock() == null) {
                throw new CoreException(ErrorType.STOCK_NOT_FOUND);
            }
            
            product.getStock().decrease(request.quantity());
        }
    }

    @Transactional
    public void increaseStocks(List<StockRequest> requests) {
        List<StockRequest> sortedRequests = requests.stream()
                .sorted(java.util.Comparator.comparing(StockRequest::productId))
                .toList();

        for (StockRequest request : sortedRequests) {
            ProductModel product = productRepository.findByIdWithLock(request.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
            
            if (product.getStock() == null) {
                throw new CoreException(ErrorType.STOCK_NOT_FOUND);
            }
            
            product.getStock().increase(request.quantity());
        }
    }

    public record StockRequest(Long productId, int quantity) {}
}
