package com.loopers.domain.payment;

/** PG 결제 접수 요청에 필요한 입력. cardNo는 PG 전달 용도로만 들고 저장하지 않는다. */
public record GatewayCommand(Long orderId, Long userId, CardType cardType, String cardNo, long amount) {

    /** cardNo(PAN)는 로그·예외에 평문 노출되지 않도록 끝 4자리만 남기고 마스킹한다. */
    @Override
    public String toString() {
        String maskedCardNo = cardNo == null || cardNo.length() < 4
            ? "****"
            : "****-****-****-" + cardNo.substring(cardNo.length() - 4);
        return "GatewayCommand{orderId=" + orderId
            + ", userId=" + userId
            + ", cardType=" + cardType
            + ", cardNo=" + maskedCardNo
            + ", amount=" + amount + "}";
    }
}
