package com.loopers.order.domain;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class ShippingDestinationTest {

    @Test
    @DisplayName("모든 필드가 채워지면 배송지 정보가 저장된다")
    void givenAllFields_whenCreate_thenStoresFields() {
        ShippingDestination destination = ShippingDestination.create(
                "김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동 202호"
        );

        assertAll(
                () -> assertThat(destination.getRecipientName()).isEqualTo("김루퍼"),
                () -> assertThat(destination.getRecipientPhone()).isEqualTo("010-1234-5678"),
                () -> assertThat(destination.getZipcode()).isEqualTo("12345"),
                () -> assertThat(destination.getAddress1()).isEqualTo("서울시 강남구"),
                () -> assertThat(destination.getAddress2()).isEqualTo("101동 202호")
        );
    }

    @Test
    @DisplayName("상세주소(address2)는 비어 있어도 생성된다")
    void givenBlankAddress2_whenCreate_thenStoresEmptyAddress2() {
        ShippingDestination destination = ShippingDestination.create(
                "김루퍼", "010-1234-5678", "12345", "서울시 강남구", null
        );

        assertThat(destination.getAddress2()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("수령인명이 비어 있으면 CoreException 이 발생한다")
    void givenBlankRecipientName_whenCreate_thenThrowsCoreException(String invalid) {
        assertThatThrownBy(() -> ShippingDestination.create(
                invalid, "010-1234-5678", "12345", "서울시 강남구", "101동"
        ))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("수령인명은 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("연락처가 비어 있으면 CoreException 이 발생한다")
    void givenBlankRecipientPhone_whenCreate_thenThrowsCoreException(String invalid) {
        assertThatThrownBy(() -> ShippingDestination.create(
                "김루퍼", invalid, "12345", "서울시 강남구", "101동"
        ))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("연락처는 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("우편번호가 비어 있으면 CoreException 이 발생한다")
    void givenBlankZipcode_whenCreate_thenThrowsCoreException(String invalid) {
        assertThatThrownBy(() -> ShippingDestination.create(
                "김루퍼", "010-1234-5678", invalid, "서울시 강남구", "101동"
        ))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("우편번호는 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("기본주소(address1)가 비어 있으면 CoreException 이 발생한다")
    void givenBlankAddress1_whenCreate_thenThrowsCoreException(String invalid) {
        assertThatThrownBy(() -> ShippingDestination.create(
                "김루퍼", "010-1234-5678", "12345", invalid, "101동"
        ))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("기본주소는 비어있을 수 없습니다.");
    }
}
