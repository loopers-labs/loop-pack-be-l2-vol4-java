# 시퀀스 다이어그램

> 레이어 표기 규칙
> - **Controller**: interfaces/api 레이어
> - **Facade**: application 레이어
> - **Service**: domain 레이어
> - **DB**: infrastructure(Repository) 레이어

---

## UC-004: 상품 찜 / 찜 취소

```mermaid
sequenceDiagram
    actor User
    participant Controller
    participant WishlistFacade
    participant ProductService
    participant WishlistService
    participant DB

    User->>Controller: POST /wishlists/{productId}
    Controller->>WishlistFacade: toggleWishlist(userId, productId)

    WishlistFacade->>ProductService: getProduct(productId)
    ProductService->>DB: findById(productId)

    alt 상품 없음
        DB-->>ProductService: null
        ProductService-->>WishlistFacade: CoreException(PRODUCT_NOT_FOUND)
        WishlistFacade-->>Controller: 404 NOT_FOUND
    else 상품 존재
        DB-->>ProductService: Product
        ProductService-->>WishlistFacade: Product

        WishlistFacade->>WishlistService: toggle(userId, productId)
        WishlistService->>DB: findByUserIdAndProductId(userId, productId)

        alt 찜 이력 있음
            DB-->>WishlistService: Wishlist
            WishlistService->>DB: delete(wishlist)
            WishlistService-->>WishlistFacade: CANCELLED
        else 찜 이력 없음
            DB-->>WishlistService: null
            WishlistService->>DB: save(new Wishlist)
            WishlistService-->>WishlistFacade: ADDED
        end

        WishlistFacade-->>Controller: WishlistInfo
        Controller-->>User: 200 OK
    end
```

---

## UC-006: 주문 요청

```mermaid
sequenceDiagram
    actor User
    participant Controller
    participant OrderFacade
    participant ProductService
    participant OrderService
    participant DB

    User->>Controller: POST /orders {productId, quantity}

    alt 주문 수량 < 1
        Controller-->>User: 400 BAD_REQUEST
    else 수량 유효
        Controller->>OrderFacade: placeOrder(userId, productId, quantity)

        OrderFacade->>ProductService: getProduct(productId)
        ProductService->>DB: findById(productId)

        alt 상품 없음
            DB-->>ProductService: null
            ProductService-->>OrderFacade: CoreException(PRODUCT_NOT_FOUND)
            OrderFacade-->>Controller: 404 NOT_FOUND
        else 상품이 ACTIVE 아님
            DB-->>ProductService: Product(INACTIVE | DELETED)
            ProductService-->>OrderFacade: CoreException(PRODUCT_NOT_ORDERABLE)
            OrderFacade-->>Controller: 400 BAD_REQUEST
        else 상품 정상
            DB-->>ProductService: Product(ACTIVE)
            ProductService-->>OrderFacade: Product

            OrderFacade->>ProductService: validateStock(productId, quantity)

            alt 재고 부족
                ProductService-->>OrderFacade: CoreException(STOCK_NOT_ENOUGH)
                OrderFacade-->>Controller: 400 BAD_REQUEST
            else 재고 충분
                ProductService-->>OrderFacade: ok

                OrderFacade->>OrderService: createOrder(userId, productId, quantity)
                OrderService->>DB: save(new Order)
                DB-->>OrderService: Order
                OrderService-->>OrderFacade: Order

                OrderFacade->>ProductService: decreaseStock(productId, quantity)
                ProductService->>DB: update stock
                DB-->>ProductService: ok

                OrderFacade-->>Controller: OrderInfo
                Controller-->>User: 200 OK
            end
        end
    end
```

---

## UC-A003: 브랜드 삭제

```mermaid
sequenceDiagram
    actor Admin
    participant Controller
    participant BrandFacade
    participant BrandService
    participant ProductService
    participant DB

    Admin->>Controller: DELETE /admin/brands/{brandId}
    Controller->>BrandFacade: deleteBrand(brandId)

    BrandFacade->>BrandService: getBrand(brandId)
    BrandService->>DB: findById(brandId)

    alt 브랜드 없음
        DB-->>BrandService: null
        BrandService-->>BrandFacade: CoreException(BRAND_NOT_FOUND)
        BrandFacade-->>Controller: 404 NOT_FOUND
    else 브랜드 존재
        DB-->>BrandService: Brand
        BrandService-->>BrandFacade: Brand

        Note over BrandFacade,DB: 소속 상품 판매중지 처리 (찜 이력은 건드리지 않음)
        BrandFacade->>ProductService: suspendAllByBrand(brandId)
        ProductService->>DB: updateStatusByBrandId(brandId, INACTIVE)
        DB-->>ProductService: ok
        ProductService-->>BrandFacade: ok

        BrandFacade->>BrandService: delete(brandId)
        BrandService->>DB: delete(brand)
        DB-->>BrandService: ok

        BrandFacade-->>Controller: ok
        Controller-->>Admin: 200 OK
    end
```

---

## UC-A005: 상품 수정

### Main Flow — 정보 수정 (이름 / 가격)

```mermaid
sequenceDiagram
    actor Admin
    participant Controller
    participant ProductFacade
    participant ProductService
    participant DB

    Admin->>Controller: PATCH /admin/products/{productId} {name, price, brandId?}
    Controller->>ProductFacade: updateProductInfo(productId, request)

    ProductFacade->>ProductService: getProduct(productId)
    ProductService->>DB: findById(productId)

    alt 상품 없음
        DB-->>ProductService: null
        ProductService-->>ProductFacade: CoreException(PRODUCT_NOT_FOUND)
        ProductFacade-->>Controller: 404 NOT_FOUND
    else 상품 존재
        DB-->>ProductService: Product
        ProductService-->>ProductFacade: Product

        alt 브랜드 변경 시도
            ProductFacade-->>Controller: CoreException(BRAND_CHANGE_NOT_ALLOWED) 400
        else 브랜드 변경 없음
            ProductFacade->>ProductService: update(productId, name, price)
            ProductService->>DB: update(product)
            DB-->>ProductService: ok
            ProductFacade-->>Controller: ProductInfo
            Controller-->>Admin: 200 OK
        end
    end
```

### Alternate Flow A — 재고 추가

```mermaid
sequenceDiagram
    actor Admin
    participant Controller
    participant ProductFacade
    participant ProductService
    participant DB

    Admin->>Controller: POST /admin/products/{productId}/stock/increase {quantity}

    alt 수량 < 1
        Controller-->>Admin: 400 BAD_REQUEST
    else 수량 유효
        Controller->>ProductFacade: increaseStock(productId, quantity)

        ProductFacade->>ProductService: getProduct(productId)
        ProductService->>DB: findById(productId)

        alt 상품 없음
            DB-->>ProductService: null
            ProductService-->>ProductFacade: CoreException(PRODUCT_NOT_FOUND)
            ProductFacade-->>Controller: 404 NOT_FOUND
        else 상품 존재
            DB-->>ProductService: Product
            ProductService-->>ProductFacade: Product
            ProductFacade->>ProductService: increaseStock(productId, quantity)
            ProductService->>DB: update stock + quantity
            DB-->>ProductService: ok
            ProductFacade-->>Controller: ProductInfo
            Controller-->>Admin: 200 OK
        end
    end
```

### Alternate Flow B — 재고 차감

```mermaid
sequenceDiagram
    actor Admin
    participant Controller
    participant ProductFacade
    participant ProductService
    participant DB

    Admin->>Controller: POST /admin/products/{productId}/stock/decrease {quantity}

    alt 수량 < 1
        Controller-->>Admin: 400 BAD_REQUEST
    else 수량 유효
        Controller->>ProductFacade: decreaseStock(productId, quantity)

        ProductFacade->>ProductService: getProduct(productId)
        ProductService->>DB: findById(productId)

        alt 상품 없음
            DB-->>ProductService: null
            ProductService-->>ProductFacade: CoreException(PRODUCT_NOT_FOUND)
            ProductFacade-->>Controller: 404 NOT_FOUND
        else 상품 존재
            DB-->>ProductService: Product
            ProductService-->>ProductFacade: Product

            alt 차감 후 재고 < 0
                ProductFacade-->>Controller: CoreException(STOCK_UNDERFLOW) 400
            else 재고 충분
                ProductFacade->>ProductService: decreaseStock(productId, quantity)
                ProductService->>DB: update stock - quantity
                DB-->>ProductService: ok
                ProductFacade-->>Controller: ProductInfo
                Controller-->>Admin: 200 OK
            end
        end
    end
```
