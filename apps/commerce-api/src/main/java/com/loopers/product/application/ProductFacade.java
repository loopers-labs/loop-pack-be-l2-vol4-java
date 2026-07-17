package com.loopers.product.application;

import com.loopers.brand.application.BrandService;
import com.loopers.brand.domain.Brand;
import com.loopers.inventory.application.InventoryService;
import com.loopers.like.application.LikeService;
import com.loopers.product.application.event.ProductActivityEventPublisher;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductDetail;
import com.loopers.product.domain.ProductSortType;
import com.loopers.support.PageSupport;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;
    private final InventoryService inventoryService;
    private final ProductActivityEventPublisher activityEventPublisher;
    private final ProductDisplayService productDisplayService = new ProductDisplayService();

    public ProductInfo createProduct(
        Long brandId, String name, String description, Long price, Integer stock) {
        brandService.ensureExists(brandId);
        Product product = productService.create(brandId, name, description, price);
        inventoryService.create(product.getId(), stock);
        return ProductInfo.from(product, stock);
    }

    /** 상품 상세 = Product + Brand + 좋아요 수 + 재고(Inventory) 를 조합한다. */
    @Transactional(readOnly = true)
    public ProductDetailInfo getProductDetail(Long productId) {
        Product product = productService.get(productId);
        Brand brand = brandService.get(product.getBrandId());
        long likeCount = likeService.getLikeCount(productId);
        int stock = inventoryService.getByProductId(productId).getAvailableQuantity();

        ProductDetail detail = productDisplayService.assembleDetail(product, brand, likeCount, stock);
        ProductDetailInfo info = ProductDetailInfo.from(detail);
        activityEventPublisher.publishViewed(productId);
        return info;
    }

    /** 랭킹에 포함된 상품만 배치 조회해 상품·브랜드·좋아요·재고 정보를 조합한다. */
    @Transactional(readOnly = true)
    public List<ProductDetailInfo> getExistingProductDetails(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }

        List<Product> products = productService.getExistingByIds(productIds);
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        List<Long> existingProductIds = products.stream().map(Product::getId).toList();

        Map<Long, Brand> brandMap = brandService.getMapByIds(brandIds);
        Map<Long, Long> likeCountMap = likeService.getLikeCounts(existingProductIds);
        Map<Long, Integer> stockMap = inventoryService.getQuantityMap(existingProductIds);

        return products.stream()
            .map(product -> productDisplayService.assembleDetail(
                product,
                brandMap.get(product.getBrandId()),
                likeCountMap.getOrDefault(product.getId(), 0L),
                stockMap.getOrDefault(product.getId(), 0)))
            .map(ProductDetailInfo::from)
            .toList();
    }

    /**
     * 대고객 상품 목록 = Product + Brand + 좋아요 수 조합 후 정렬, page/size 페이지네이션.
     */
    @Transactional(readOnly = true)
    public Page<ProductDetailInfo> getProducts(
        Long brandId, ProductSortType sortType, int page, int size) {
        List<Product> products =
            brandId != null ? productService.getByBrandId(brandId) : productService.getAll();

        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        List<Long> productIds = products.stream().map(Product::getId).toList();

        Map<Long, Brand> brandMap = brandService.getMapByIds(brandIds);
        Map<Long, Long> likeCountMap = likeService.getLikeCounts(productIds);
        Map<Long, Integer> stockMap = inventoryService.getQuantityMap(productIds);

        List<ProductDetailInfo> assembled =
            productDisplayService
                .assembleList(products, brandMap, likeCountMap, stockMap, sortType)
                .stream()
                .map(ProductDetailInfo::from)
                .toList();
        return PageSupport.paginate(assembled, page, size);
    }

    /** 관리자 상품 목록 = 운영용 상품 정보(재고 포함), 최신순 + page/size 페이지네이션. */
    @Transactional(readOnly = true)
    public Page<ProductInfo> getProductsForAdmin(Long brandId, int page, int size) {
        List<Product> products =
            brandId != null ? productService.getByBrandId(brandId) : productService.getAll();

        List<Long> productIds = products.stream().map(Product::getId).toList();
        Map<Long, Integer> stockMap = inventoryService.getQuantityMap(productIds);

        List<ProductInfo> infos =
            products.stream()
                .sorted(Comparator.comparing(Product::getId).reversed())
                .map(product -> ProductInfo.from(product, stockMap.getOrDefault(product.getId(), 0)))
                .toList();
        return PageSupport.paginate(infos, page, size);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProductForAdmin(Long productId) {
        Product product = productService.get(productId);
        int stock = inventoryService.getByProductId(productId).getAvailableQuantity();
        return ProductInfo.from(product, stock);
    }

    public ProductInfo updateProduct(
        Long id, String name, String description, Long price, Integer stock) {
        Product product = productService.update(id, name, description, price);
        inventoryService.setQuantity(id, stock);
        return ProductInfo.from(product, stock);
    }

    public void deleteProduct(Long id) {
        productService.delete(id);
    }
}
