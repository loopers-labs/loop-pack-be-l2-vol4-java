package com.loopers.payment.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class PaymentCard {

    private static final Pattern CARD_NO_PATTERN = Pattern.compile("^(\\d{4})-\\d{4}-\\d{4}-(\\d{4})$");

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType type;

    @Column(name = "masked_card_no", nullable = false)
    private String maskedNo;

    private PaymentCard(CardType type, String maskedNo) {
        this.type = requireType(type);
        this.maskedNo = requireMaskedNo(maskedNo);
    }

    public static PaymentCard of(CardType type, String cardNo) {
        return new PaymentCard(type, mask(cardNo));
    }

    private static CardType requireType(CardType type) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 타입은 비어있을 수 없습니다.");
        }
        return type;
    }

    private static String mask(String cardNo) {
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 비어있을 수 없습니다.");
        }

        Matcher matcher = CARD_NO_PATTERN.matcher(cardNo);
        if (!matcher.matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호 형식이 올바르지 않습니다.");
        }
        return "%s-****-****-%s".formatted(matcher.group(1), matcher.group(2));
    }

    private static String requireMaskedNo(String maskedNo) {
        if (maskedNo == null || maskedNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "마스킹된 카드 번호는 비어있을 수 없습니다.");
        }
        return maskedNo;
    }
}
