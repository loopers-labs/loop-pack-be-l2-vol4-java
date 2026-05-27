package com.loopers.application.product;

import com.loopers.application.common.PageCriteria;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductCatalogService;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductCatalogService productCatalogService = new ProductCatalogService();

    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        BrandModel brand = getBrand(brandId);
        ProductModel product = productRepository.save(new ProductModel(brandId, name, description, price, stock));
        return ProductInfo.from(productCatalogService.getProductDetail(product, brand));
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        ProductModel product = getProductModel(id);
        BrandModel brand = getBrand(product.getBrandId());
        ProductDetail productDetail = productCatalogService.getProductDetail(product, brand);
        return ProductInfo.from(productDetail);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts() {
        return getAllProducts(null);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts(String sort) {
        return getAllProducts(null, sort, null, null);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts(Long brandId, String sort, Integer page, Integer size) {
        if (brandId != null) {
            getBrand(brandId);
        }

        ProductSort productSort = ProductSort.from(sort);
        PageCriteria pageCriteria = PageCriteria.of(page, size);
        List<ProductModel> products = brandId == null
            ? productRepository.findAll(productSort, pageCriteria.page(), pageCriteria.size())
            : productRepository.findAllByBrandId(brandId, productSort, pageCriteria.page(), pageCriteria.size());

        Map<Long, BrandModel> brandsById = findBrandsByProduct(products);
        List<ProductDetail> productDetails = productCatalogService.getProductDetails(products, brandsById);
        return productDetails.stream()
            .map(ProductInfo::from)
            .toList();
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = getProductModel(id);
        product.update(name, description, price, stock);
        ProductModel savedProduct = productRepository.save(product);
        BrandModel brand = getBrand(savedProduct.getBrandId());
        return ProductInfo.from(productCatalogService.getProductDetail(savedProduct, brand));
    }

    @Transactional
    public void deleteProduct(Long id) {
        ProductModel product = getProductModel(id);
        product.delete();
        productRepository.save(product);
    }

    private ProductModel getProductModel(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    private BrandModel getBrand(Long id) {
        return brandRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 브랜드를 찾을 수 없습니다."));
    }

    private Map<Long, BrandModel> findBrandsByProduct(List<ProductModel> products) {
        return products.stream()
            .map(ProductModel::getBrandId)
            .distinct()
            .map(this::getBrand)
            .collect(Collectors.toMap(BrandModel::getId, Function.identity()));
    }
}
