package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntryTokenServiceTest {

    private EntryTokenService entryTokenService;
    private EntryTokenRepository entryTokenRepository;

    @BeforeEach
    void setUp() {
        entryTokenRepository = mock(EntryTokenRepository.class);
        entryTokenService = new EntryTokenService(entryTokenRepository);
    }

    @DisplayName("입장 토큰을 조회할 때, ")
    @Nested
    class Find {

        @DisplayName("Repository가 반환한 값을 그대로 반환한다.")
        @Test
        void returnsRepositoryValue_whenTokenExists() {
            // given
            Long userId = 1L;
            when(entryTokenRepository.find(userId)).thenReturn(Optional.of("abc-123"));

            // when
            Optional<String> result = entryTokenService.find(userId);

            // then
            assertThat(result).contains("abc-123");
        }
    }

    @DisplayName("입장 토큰을 검증할 때, ")
    @Nested
    class Verify {

        @DisplayName("보낸 토큰이 저장된 토큰과 일치하면 예외 없이 통과한다.")
        @Test
        void doesNotThrow_whenTokenMatches() {
            // given
            Long userId = 1L;
            when(entryTokenRepository.find(userId)).thenReturn(Optional.of("abc-123"));

            // when & then
            assertDoesNotThrow(() -> entryTokenService.verify(userId, "abc-123"));
        }

        @DisplayName("토큰이 없으면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbiddenException_whenTokenDoesNotExist() {
            // given
            Long userId = 1L;
            when(entryTokenRepository.find(userId)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () -> entryTokenService.verify(userId, "abc-123"));

            // then
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN),
                    () -> assertThat(result.getMessage()).isEqualTo("입장 토큰이 없거나 만료되었습니다. 대기열을 통해 다시 진입해주세요.")
            );
        }

        @DisplayName("보낸 토큰이 저장된 토큰과 다르면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbiddenException_whenTokenDoesNotMatch() {
            // given
            Long userId = 1L;
            when(entryTokenRepository.find(userId)).thenReturn(Optional.of("abc-123"));

            // when
            CoreException result = assertThrows(CoreException.class, () -> entryTokenService.verify(userId, "wrong-token"));

            // then
            assertAll(
                    () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN),
                    () -> assertThat(result.getMessage()).isEqualTo("유효하지 않은 입장 토큰입니다. 대기열을 통해 다시 진입해주세요.")
            );
        }
    }
}
