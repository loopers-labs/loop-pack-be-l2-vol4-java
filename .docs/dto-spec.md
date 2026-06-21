# DTO Spec

## Purpose

This document defines the current API DTO contract for the commerce implementation.
It is a helper document only and is not part of the submission set.

## Header Contract

| Header | Required By | Description |
| --- | --- | --- |
| `X-Loopers-LoginId` | `user_required` API | Login ID. Used as existing `identity` module `userId` reference in volume-2. |
| `X-Loopers-LoginPw` | `user_required` API | Login password. Passed to the existing `identity` boundary; not stored in volume-2 domain models. |
| `X-Loopers-Ldap` | `ldap_required` API | Admin identifier. Must be `loopers.admin`. |

## Common Response

### `ApiResponse<T>`

| Field | Type | Description |
| --- | --- | --- |
| `meta.result` | `SUCCESS \| FAIL` | Top-level result flag. |
| `meta.message` | `string` | Human-readable message for success or failure. |
| `meta.errorCode` | `string?` | Present only when `meta.result = FAIL`. |
| `data` | `T?` | Actual payload. |

### `PageResponse<T>`

| Field | Type | Description |
| --- | --- | --- |
| `items` | `List<T>` | Current page items. |
| `pageInfo` | `PageInfo` | Paging metadata. |

### `PageInfo`

| Field | Type | Description |
| --- | --- | --- |
| `page` | `int` | 0-based page number. Default is `0`. |
| `size` | `int` | Requested page size. Default is `20`. |
| `totalElements` | `long` | Total item count. |
| `totalPages` | `int` | Total page count. |
| `hasNext` | `boolean` | Whether next page exists. |
| `hasPrevious` | `boolean` | Whether previous page exists. |
| `isFirst` | `boolean` | Whether current page is the first page. |
| `isLast` | `boolean` | Whether current page is the last page. |
| `sort` | `SortInfo` | Current sort information. |

### `SortInfo`

| Field | Type | Description |
| --- | --- | --- |
| `field` | `string` | Sorted field name. |
| `direction` | `ASC \| DESC` | Sort direction. |

### `ProductSort`

| Value | Description |
| --- | --- |
| `latest` | Newest products first. Default sort. |
| `price_asc` | Lowest price first. |
| `likes_desc` | Highest like count first. |

## Product DTO

### `ProductListItemResponse`

| Field | Type | Description |
| --- | --- | --- |
| `productId` | `long` | Product identifier. |
| `name` | `string` | Product name. |
| `price` | `long` | Product price. |
| `status` | `ProductStatus` | `ON_SALE`, `SOLD_OUT`, `STOPPED`. |
| `brandName` | `string` | Display brand name. |
| `likeCount` | `long` | Current like count. |

### `ProductDetailResponse`

| Field | Type | Description |
| --- | --- | --- |
| `productId` | `long` | Product identifier. |
| `name` | `string` | Product name. |
| `description` | `string` | Product description. |
| `price` | `long` | Product price. |
| `status` | `ProductStatus` | `ON_SALE`, `SOLD_OUT`, `STOPPED`. |
| `stockQuantity` | `int` | Current stock quantity. |
| `brand` | `BrandSummaryResponse` | Brand summary object. |
| `likeCount` | `long` | Current like count. |
| `liked` | `boolean` | Logged-in user liked flag. Anonymous requests return `false`. |

### `BrandSummaryResponse`

| Field | Type | Description |
| --- | --- | --- |
| `brandId` | `long` | Brand identifier. |
| `name` | `string` | Brand display name. |

## API Mapping

| API | Response DTO |
| --- | --- |
| `GET /api/v1/products?brandId&sort&page&size` | `ApiResponse<PageResponse<ProductListItemResponse>>` |
| `GET /api/v1/products/{productId}` | `ApiResponse<ProductDetailResponse>` |
| `GET /api/v1/brands/{brandId}` | `ApiResponse<BrandSummaryResponse>` |

## ProductLike DTO

### `ProductLikeResponse`

| Field | Type | Description |
| --- | --- | --- |
| `productId` | `long` | Product identifier. |
| `name` | `string` | Product name. |
| `price` | `long` | Product price. |
| `status` | `ProductStatus` | `ON_SALE`, `SOLD_OUT`, `STOPPED`. |
| `brandName` | `string` | Display brand name. |
| `likeCount` | `long` | Current like count. |
| `liked` | `boolean` | Whether the current user liked the product. |

### API Mapping

| API | Response DTO |
| --- | --- |
| `POST /api/v1/products/{productId}/likes` | `ApiResponse<ProductLikeResponse>` |
| `DELETE /api/v1/products/{productId}/likes` | `ApiResponse<ProductLikeResponse>` |
| `GET /api/v1/users/{userId}/likes` | `ApiResponse<PageResponse<ProductLikeResponse>>` |

## Rules

- Use `items + pageInfo` for every paged response.
- Paged APIs use 0-based page numbers. Default page is `0`, default size is `20`.
- Keep `sort` as a single value, not a list.
- Product list sort values are `latest`, `price_asc`, and `likes_desc`.
- List responses should use summary DTOs.
- Detail responses should use expanded DTOs.
- Like responses should reuse one DTO for mutation and list views.
- For `GET /api/v1/users/{userId}/likes`, the path `userId` must match `X-Loopers-LoginId`.
- Anonymous product requests should still return the same DTO shape.

## Order DTO

### Coupon Identifier Rule

- `OrderCreateRequest.couponId` is an issued coupon ID owned by the current user.
- `POST /api/v1/coupons/{couponId}/issue` uses a coupon template ID in its path.
- Admin coupon APIs also use coupon template IDs in their paths.
- Keep these identifiers distinct even though the external field name is `couponId`.

### `OrderCreateRequest`

| Field | Type | Description |
| --- | --- | --- |
| `items` | `List<OrderCreateItemRequest>` | Ordered product items. |
| `couponId` | `long?` | Optional issued coupon identifier owned by the current user. |

### `OrderCreateItemRequest`

| Field | Type | Description |
| --- | --- | --- |
| `productId` | `long` | Product identifier. |
| `quantity` | `int` | Order quantity. |

### Rules

- Keep the request body to `items` and optional issued `couponId`.
- Do not include delivery, payment method, or calculated amount fields in the request.
- Let the server calculate totals and status.

### `OrderCreateResponse`

| Field | Type | Description |
| --- | --- | --- |
| `orderId` | `long` | Order identifier. |
| `orderStatus` | `OrderStatus` | Created order status. |
| `originalAmount` | `long` | Product total before discount. |
| `discountAmount` | `long` | Applied coupon discount. |
| `finalAmount` | `long` | Final payment amount. |
| `couponId` | `long?` | Applied issued coupon identifier. |

### `OrderListItemResponse`

| Field | Type | Description |
| --- | --- | --- |
| `orderId` | `long` | Order identifier. |
| `orderStatus` | `OrderStatus` | Current order status. |
| `paymentStatus` | `PaymentStatus` | Current payment status. Zero amount orders return `NOT_REQUIRED`. |
| `originalAmount` | `long` | Product total before discount. |
| `discountAmount` | `long` | Applied coupon discount. |
| `finalAmount` | `long` | Final payment amount. |
| `couponId` | `long?` | Applied issued coupon identifier. |
| `createdAt` | `datetime` | Order creation time. |

### `OrderDetailResponse`

| Field | Type | Description |
| --- | --- | --- |
| `orderId` | `long` | Order identifier. |
| `orderStatus` | `OrderStatus` | Current order status. |
| `paymentStatus` | `PaymentStatus` | Current payment status. Zero amount orders return `NOT_REQUIRED`. |
| `failureReason` | `string?` | Failure reason when present. |
| `originalAmount` | `long` | Product total before discount. |
| `discountAmount` | `long` | Applied coupon discount. |
| `finalAmount` | `long` | Final payment amount. |
| `couponId` | `long?` | Applied issued coupon identifier. |
| `items` | `List<OrderDetailItemResponse>` | Snapshot item list. |

### `OrderDetailItemResponse`

| Field | Type | Description |
| --- | --- | --- |
| `productId` | `long` | Product identifier. |
| `productName` | `string` | Product name snapshot. |
| `quantity` | `int` | Ordered quantity. |
| `unitPrice` | `long` | Unit price at order time. |
| `lineAmount` | `long` | `unitPrice * quantity`. |

### API Mapping

| API | Response DTO |
| --- | --- |
| `POST /api/v1/orders` | `ApiResponse<OrderCreateResponse>` |
| `GET /api/v1/orders?startAt&endAt` | `ApiResponse<List<OrderListItemResponse>>` |
| `GET /api/v1/orders/{orderId}` | `ApiResponse<OrderDetailResponse>` |

## Admin DTO

### `AdminBrandRequest`

| Field | Type | Description |
| --- | --- | --- |
| `name` | `string` | Brand display name. |
| `description` | `string` | Brand description. |

### `AdminBrandResponse`

| Field | Type | Description |
| --- | --- | --- |
| `brandId` | `long` | Brand identifier. |
| `name` | `string` | Brand display name. |
| `description` | `string` | Brand description. |

### `AdminProductCreateRequest`

| Field | Type | Description |
| --- | --- | --- |
| `brandId` | `long` | Existing brand identifier. |
| `name` | `string` | Product name. |
| `description` | `string` | Product description. |
| `price` | `long` | Product price. |
| `stockQuantity` | `int` | Product stock quantity. |
| `status` | `ProductStatus` | Initial sale status. |

### `AdminProductUpdateRequest`

| Field | Type | Description |
| --- | --- | --- |
| `name` | `string` | Product name. |
| `description` | `string` | Product description. |
| `price` | `long` | Product price. |
| `stockQuantity` | `int` | Product stock quantity. |
| `status` | `ProductStatus` | Sale status. |

### Rule

- `AdminProductUpdateRequest` does not include `brandId`; product brand cannot be changed after creation.

### `AdminProductResponse`

| Field | Type | Description |
| --- | --- | --- |
| `productId` | `long` | Product identifier. |
| `brandId` | `long` | Brand identifier. |
| `name` | `string` | Product name. |
| `description` | `string` | Product description. |
| `price` | `long` | Product price. |
| `status` | `ProductStatus` | Sale status. |
| `stockQuantity` | `int` | Current stock quantity. |
| `likeCount` | `long` | Current like count. |

### `AdminOrderListItemResponse`

| Field | Type | Description |
| --- | --- | --- |
| `orderId` | `long` | Order identifier. |
| `userId` | `string` | Existing identity module user identifier. |
| `orderStatus` | `OrderStatus` | Current order status. |
| `paymentStatus` | `PaymentStatus` | Current payment status. Zero amount orders return `NOT_REQUIRED`. |
| `originalAmount` | `long` | Product total before discount. |
| `discountAmount` | `long` | Applied coupon discount. |
| `finalAmount` | `long` | Final payment amount. |
| `couponId` | `long?` | Applied issued coupon identifier. |
| `createdAt` | `datetime` | Order creation time. |

### Admin API Mapping

| API | Response DTO |
| --- | --- |
| `GET /api-admin/v1/brands?page&size` | `ApiResponse<PageResponse<AdminBrandResponse>>` |
| `GET /api-admin/v1/brands/{brandId}` | `ApiResponse<AdminBrandResponse>` |
| `POST /api-admin/v1/brands` | `ApiResponse<AdminBrandResponse>` |
| `PUT /api-admin/v1/brands/{brandId}` | `ApiResponse<AdminBrandResponse>` |
| `DELETE /api-admin/v1/brands/{brandId}` | `ApiResponse<Void>` |
| `GET /api-admin/v1/products?page&size&brandId` | `ApiResponse<PageResponse<AdminProductResponse>>` |
| `GET /api-admin/v1/products/{productId}` | `ApiResponse<AdminProductResponse>` |
| `POST /api-admin/v1/products` | `ApiResponse<AdminProductResponse>` |
| `PUT /api-admin/v1/products/{productId}` | `ApiResponse<AdminProductResponse>` |
| `DELETE /api-admin/v1/brands/{brandId}` | `ApiResponse<Void>`; internally sets `Brand.deletedAt` and related products to `ProductStatus.STOPPED` |
| `DELETE /api-admin/v1/products/{productId}` | `ApiResponse<Void>`; internally sets `ProductStatus.STOPPED` |
| `GET /api-admin/v1/orders?page&size` | `ApiResponse<PageResponse<AdminOrderListItemResponse>>` |
| `GET /api-admin/v1/orders/{orderId}` | `ApiResponse<OrderDetailResponse>` |

## Coupon DTO

### `CouponTemplateRequest`

| Field | Type | Description |
| --- | --- | --- |
| `name` | `string` | Coupon template display name. |
| `type` | `FIXED \| RATE` | Fixed amount or rate discount. |
| `value` | `long` | Positive discount amount or rate from 1 to 100. |
| `minOrderAmount` | `long?` | Optional minimum original order amount. |
| `maxIssuesPerUser` | `int` | Positive per-user issue limit. |
| `expiredAt` | `datetime` | Required future expiration time. |

### `IssuedCouponResponse`

| Field | Type | Description |
| --- | --- | --- |
| `couponId` | `long` | Issued coupon identifier used by order requests. |
| `couponTemplateId` | `long` | Coupon template identifier. |
| `couponName` | `string` | Current template display name. |
| `type` | `FIXED \| RATE` | Snapshotted discount type. |
| `value` | `long` | Snapshotted discount value. |
| `minOrderAmount` | `long?` | Snapshotted minimum original order amount. |
| `expiredAt` | `datetime` | Snapshotted expiration time. |
| `status` | `AVAILABLE \| USED \| EXPIRED` | Calculated issued coupon state. |
| `issuedAt` | `datetime` | Issue time. |
| `usedAt` | `datetime?` | Use time when present. |

### API Mapping

| API | Response DTO |
| --- | --- |
| `POST /api/v1/coupons/{couponId}/issue` | `ApiResponse<IssuedCouponResponse>`; path ID is a template ID |
| `GET /api/v1/users/me/coupons?page&size` | `ApiResponse<PageResponse<IssuedCouponResponse>>` |
| `GET /api-admin/v1/coupons?page&size` | `ApiResponse<PageResponse<CouponTemplateResponse>>` |
| `GET /api-admin/v1/coupons/{couponId}` | `ApiResponse<CouponTemplateResponse>` |
| `POST /api-admin/v1/coupons` | `ApiResponse<CouponTemplateResponse>` |
| `PUT /api-admin/v1/coupons/{couponId}` | `ApiResponse<CouponTemplateResponse>` |
| `DELETE /api-admin/v1/coupons/{couponId}` | `ApiResponse<Void>` |
| `GET /api-admin/v1/coupons/{couponId}/issues?page&size` | `ApiResponse<PageResponse<CouponIssueResponse>>` |

## Payment DTO

### `PaymentAuthorizeRequest`

| Field | Type | Description |
| --- | --- | --- |
| `orderId` | `long` | Order identifier. |
| `amount` | `long` | Authorization amount. |
| `idempotencyKey` | `string` | Duplicate request guard. |

### `PaymentAuthorizeResponse`

| Field | Type | Description |
| --- | --- | --- |
| `transactionKey` | `string` | Payment transaction key. |
| `result` | `PaymentResult` | Authorization result. |

### `PaymentCaptureRequest`

| Field | Type | Description |
| --- | --- | --- |
| `transactionKey` | `string` | Payment transaction key. |

### `PaymentCaptureResponse`

| Field | Type | Description |
| --- | --- | --- |
| `result` | `PaymentResult` | Capture result. |

### `PaymentVoidRequest`

| Field | Type | Description |
| --- | --- | --- |
| `transactionKey` | `string` | Payment transaction key. |

### `PaymentVoidResponse`

| Field | Type | Description |
| --- | --- | --- |
| `result` | `PaymentResult` | Void result. |

### `PaymentResult`

| Field | Type | Description |
| --- | --- | --- |
| `success` | `boolean` | Whether the payment operation succeeded. |
| `message` | `string?` | Optional provider message. |

### Contract Mapping

| Contract | Request DTO | Response DTO |
| --- | --- | --- |
| `PaymentGateway.authorize` | `PaymentAuthorizeRequest` | `PaymentAuthorizeResponse` |
| `PaymentGateway.capture` | `PaymentCaptureRequest` | `PaymentCaptureResponse` |
| `PaymentGateway.voidAuthorization` | `PaymentVoidRequest` | `PaymentVoidResponse` |

## DataPlatform DTO

### `OrderPaidEvent`

| Field | Type | Description |
| --- | --- | --- |
| `orderId` | `long` | Order identifier. |
| `userId` | `string` | Existing identity module user identifier. |
| `originalAmount` | `long` | Product total before discount. |
| `discountAmount` | `long` | Applied coupon discount. |
| `finalAmount` | `long` | Final paid amount. |
| `paidAt` | `datetime` | Payment completion time. |
| `items` | `List<OrderPaidItem>` | Order snapshot items. |

### `OrderPaidItem`

| Field | Type | Description |
| --- | --- | --- |
| `productId` | `long` | Product identifier. |
| `productName` | `string` | Product name snapshot. |
| `quantity` | `int` | Paid quantity. |
| `unitPrice` | `long` | Unit price at payment time. |
| `lineAmount` | `long` | `unitPrice * quantity`. |

### Contract Mapping

| Contract | Payload |
| --- | --- |
| `DataPlatformClient.sendOrderPaid` | `OrderPaidEvent` |

## Rules

- Use `items + pageInfo` for every paged response.
- Paged APIs use 0-based page numbers. Default page is `0`, default size is `20`.
- Keep `sort` as a single value, not a list.
- List responses should use summary DTOs.
- Detail responses should use expanded DTOs.
- Like responses should reuse one DTO for mutation and list views.
- Payment gateway DTOs should stay internal to the application boundary.
- Data platform payloads should be order snapshot based.
- Anonymous product requests should still return the same DTO shape.

## Next Step

- No remaining DTO decision.
