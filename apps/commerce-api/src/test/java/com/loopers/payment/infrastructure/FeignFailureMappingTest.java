package com.loopers.payment.infrastructure;

import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import feign.FeignException;
import feign.Request;
import feign.RequestLine;
import feign.Retryer;
import feign.RetryableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * feign 기본 클라이언트가 각 실패 모드를 어떤 예외로 던지는지 실측해, 재시도 predicate 설계의 전제를 검증한다.
 * 연결 실패 = 안 닿음(재시도 안전), read 타임아웃·HTTP 에러 = 닿음(재시도 위험).
 */
class FeignFailureMappingTest {

    interface ProbeClient {
        @RequestLine("GET /slow")
        String slow();

        @RequestLine("GET /notfound")
        String notFound();
    }

    private HttpServer server;
    private int port;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/slow", ex -> {
            try {
                Thread.sleep(1_000); // readTimeout(300ms)보다 길게 — read 타임아웃 유발
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.createContext("/notfound", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ProbeClient client(String baseUrl) {
        return Feign.builder()
                .options(new Request.Options(300, TimeUnit.MILLISECONDS, 300, TimeUnit.MILLISECONDS, true))
                .retryer(Retryer.NEVER_RETRY) // feign 내부 재시도 끄고 첫 예외를 그대로 관찰
                .target(ProbeClient.class, baseUrl);
    }

    private int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort(); // 닫힌 직후 포트 → 연결 거부
        }
    }

    @Test
    @DisplayName("연결 거부는 RetryableException(cause=ConnectException) — 안 닿음이라 재시도 안전")
    void connectionRefused_isRetryableExceptionWithConnectExceptionCause() throws IOException {
        ProbeClient client = client("http://localhost:" + closedPort());

        Throwable thrown = catchThrowable(client::notFound);

        assertThat(thrown).isInstanceOf(RetryableException.class);
        assertThat(thrown.getCause()).isInstanceOf(ConnectException.class);
    }

    @Test
    @DisplayName("read 타임아웃은 RetryableException(cause=SocketTimeoutException 'Read timed out') — 닿음이라 재시도 위험")
    void readTimeout_isRetryableExceptionWithSocketTimeoutCause() {
        ProbeClient client = client("http://localhost:" + port);

        Throwable thrown = catchThrowable(client::slow);

        assertThat(thrown).isInstanceOf(RetryableException.class);
        assertThat(thrown.getCause()).isInstanceOf(SocketTimeoutException.class);
        assertThat(thrown.getCause().getMessage()).contains("Read timed out");
    }

    @Test
    @DisplayName("404 응답은 FeignException.NotFound — 닿음이라 재시도 안 함")
    void notFound_isFeignNotFound() {
        ProbeClient client = client("http://localhost:" + port);

        Throwable thrown = catchThrowable(client::notFound);

        assertThat(thrown).isInstanceOf(FeignException.NotFound.class);
    }
}
