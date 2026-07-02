package com.loopers.product.application;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public Product create(Long brandId, String name, String description, Long price) {
        return productRepository.save(new Product(brandId, name, description, price));
    }

    public void saveAll(List<Product> products) {
        products.forEach(productRepository::save);
    }

    public Product get(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    public List<Product> getAll() {
        return productRepository.findAll();
    }

    public List<Product> getByBrandId(Long brandId) {
        return productRepository.findByBrandId(brandId);
    }

    /** 주문에 필요한 상품들을 조회한다. 일부라도 존재하지 않으면 예외를 던진다. */
    public List<Product> getAllByIds(Collection<Long> ids) {
        List<Product> products = productRepository.findAllByIds(ids);
        if (products.size() != ids.stream().distinct().count()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품이 포함되어 있습니다.");
        }
        return products;
    }

    /** 존재하는 상품만 조회한다. 삭제되었거나 없는 상품은 결과에서 제외된다. */
    public List<Product> getExistingByIds(Collection<Long> ids) {
        return productRepository.findAllByIds(ids);
    }

    public Product update(Long id, String name, String description, Long price) {
        Product product = get(id);
        product.update(name, description, price);
        return productRepository.save(product);
    }

    public void delete(Long id) {
        Product product = get(id);
        product.delete();
        productRepository.save(product);
    }

    /** 브랜드 삭제 시 해당 브랜드의 상품을 함께 논리 삭제한다. */
    public void deleteAllByBrandId(Long brandId) {
        List<Product> products = productRepository.findByBrandId(brandId);
        for (Product product : products) {
            product.delete();
            productRepository.save(product);
        }
    }
}
