package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductCommand;
import com.loopers.domain.product.ProductDetail;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductApplicationService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final LikeRepository likeRepository;

    @Transactional
    public ProductInfo.Created register(ProductCriteria.Register command) {
        if (brandRepository.find(command.brandId()).isEmpty()) {
            throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
        }
        Product product = Product.create(
                command.brandId(),
                command.name(),
                Money.of(command.price()),
                Stock.of(command.stock())
        );
        Product saved = productRepository.save(product);
        return ProductInfo.Created.from(saved);
    }

    @Transactional(readOnly = true)
    public ProductInfo.Detail getProduct(Long id) {
        Product product = productRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        Brand brand = brandRepository.find(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        long likeCount = likeRepository.countByProductId(id);
        return ProductInfo.Detail.from(new ProductDetail(product, brand.getId(), brand.getName(), likeCount));
    }

    @Transactional(readOnly = true)
    public PageResult<ProductInfo.ListItem> getAllProducts(ProductCriteria.GetAll command) {
        ProductCommand.Search search = new ProductCommand.Search(
                command.page(),
                command.size(),
                command.brandId(),
                command.sortType()
        );
        PageResult<Product> products = productRepository.findAll(search);

        Set<Long> brandIds = products.content().stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());
        Map<Long, Brand> brandById = brandRepository.findAllByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        Set<Long> productIds = products.content().stream()
                .map(Product::getId)
                .collect(Collectors.toSet());
        Map<Long, Long> likeCountByProductId = likeRepository.countByProductIds(productIds);

        return products.map(product -> {
            Brand brand = brandById.get(product.getBrandId());
            if (brand == null) {
                throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
            }
            long likeCount = likeCountByProductId.getOrDefault(product.getId(), 0L);
            return ProductInfo.ListItem.from(
                    new ProductDetail(product, brand.getId(), brand.getName(), likeCount));
        });
    }

    @Transactional
    public void modify(ProductCriteria.Modify command) {
        Product product = findOrThrow(command.id());
        product.modify(command.name(), Money.of(command.price()));
        productRepository.update(product);
    }

    @Transactional
    public void adjustStock(ProductCriteria.AdjustStock command) {
        Product product = findOrThrow(command.id());
        product.adjustStock(command.newQuantity());
        productRepository.update(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = findOrThrow(id);
        product.delete();
        productRepository.update(product);
    }

    private Product findOrThrow(Long id) {
        return productRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }
}
