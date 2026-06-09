package com.loopers.product.application;

import com.loopers.brand.application.BrandService;
import com.loopers.brand.domain.BrandModel;
import com.loopers.like.application.LikeService;
import com.loopers.product.domain.ProductDetail;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductSortType;
import com.loopers.support.PageSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Transactional
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;
    private final ProductDisplayService productDisplayService = new ProductDisplayService();

    public ProductInfo createProduct(
        Long brandId, String name, String description, Long price, Integer stock) {
        brandService.ensureExists(brandId);
        return ProductInfo.from(productService.create(brandId, name, description, price, stock));
    }

    /** 상품 상세 = Product + Brand + 좋아요 수를 도메인 서비스에서 조합한다. */
    @Transactional(readOnly = true)
    public ProductDetailInfo getProductDetail(Long productId) {
        ProductModel product = productService.get(productId);
        BrandModel brand = brandService.get(product.getBrandId());
        long likeCount = likeService.getLikeCount(productId);

        ProductDetail detail = productDisplayService.assembleDetail(product, brand, likeCount);
        return ProductDetailInfo.from(detail);
    }

    /**
     * 대고객 상품 목록 = Product + Brand + 좋아요 수 조합 후 정렬, page/size 페이지네이션.
     */
    @Transactional(readOnly = true)
    public Page<ProductDetailInfo> getProducts(
        Long brandId, ProductSortType sortType, int page, int size) {
        List<ProductModel> products =
            brandId != null ? productService.getByBrandId(brandId) : productService.getAll();

        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();

        Map<Long, BrandModel> brandMap = brandService.getMapByIds(brandIds);
        Map<Long, Long> likeCountMap = likeService.getLikeCounts(productIds);

        List<ProductDetailInfo> assembled =
            productDisplayService.assembleList(products, brandMap, likeCountMap, sortType).stream()
                .map(ProductDetailInfo::from)
                .toList();
        return PageSupport.paginate(assembled, page, size);
    }

    /** 관리자 상품 목록 = 운영용 상품 정보(재고 포함), 최신순 + page/size 페이지네이션. */
    @Transactional(readOnly = true)
    public Page<ProductInfo> getProductsForAdmin(Long brandId, int page, int size) {
        List<ProductModel> products =
            brandId != null ? productService.getByBrandId(brandId) : productService.getAll();

        List<ProductInfo> infos =
            products.stream()
                .sorted(Comparator.comparing(ProductModel::getId).reversed())
                .map(ProductInfo::from)
                .toList();
        return PageSupport.paginate(infos, page, size);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProductForAdmin(Long productId) {
        return ProductInfo.from(productService.get(productId));
    }

    public ProductInfo updateProduct(
        Long id, String name, String description, Long price, Integer stock) {
        return ProductInfo.from(productService.update(id, name, description, price, stock));
    }

    public void deleteProduct(Long id) {
        productService.delete(id);
    }
}
