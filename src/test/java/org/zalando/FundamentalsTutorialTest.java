package org.zalando;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.zalando.riptide.Http;
import org.zalando.util.Product;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.MOVED_PERMANENTLY;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.Series.CLIENT_ERROR;
import static org.springframework.http.HttpStatus.Series.SERVER_ERROR;
import static org.springframework.http.HttpStatus.Series.SUCCESSFUL;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.zalando.riptide.Bindings.anySeries;
import static org.zalando.riptide.Bindings.anyStatus;
import static org.zalando.riptide.Bindings.on;
import static org.zalando.riptide.Navigators.series;
import static org.zalando.riptide.Navigators.status;
import static org.zalando.riptide.PassRoute.pass;
import static org.zalando.util.AssertUtil.assertThrowsWithCause;
import static org.zalando.util.MockWebServerUtil.emptyMockResponse;
import static org.zalando.util.MockWebServerUtil.getBaseUrl;
import static org.zalando.util.MockWebServerUtil.verify;

@Slf4j
class FundamentalsTutorialTest {
    private final MockWebServer server = new MockWebServer();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Http http = Http.builder()
            .executor(executor)
            .requestFactory(new SimpleClientHttpRequestFactory())
            .baseUrl(getBaseUrl(server))
            .build();

    @SneakyThrows
    @AfterEach
    void shutdown() {
        executor.shutdown();
        server.shutdown();
    }

    @Test
    void shouldSendGetRequest() {
        server.enqueue(emptyMockResponse());

        http.get("/products")
                .header("X-Foo", "bar")
                .call(pass())
                .join();

        verify(server, 1, "/products", GET.toString());
    }


    @Test
    void shouldSendPostRequest() {
        server.enqueue(emptyMockResponse());

        http.post("/products")
                .header("X-Foo", "bar")
                .contentType(MediaType.APPLICATION_JSON)
                .body("str_1")
                .call(pass())
                .join();

        verify(server, 1, "/products", POST.toString());
    }


    @Test
    void shouldProcessSimpleRouting() {
        server.enqueue(new MockResponse().setResponseCode(OK.value()));
        server.enqueue(new MockResponse().setResponseCode(NOT_FOUND.value()));
        server.enqueue(new MockResponse().setResponseCode(NO_CONTENT.value()));

        for (int i = 0; i < 3; i++) {
            http.get("/products/{id}", 100)
                    .dispatch(status(),
                            on(OK).call(Product.class, product -> log.info("Product: " + product)),
                            on(NOT_FOUND).call(response -> log.warn("Product not found")),
                            anyStatus().call(pass()))
                    .join();
        }

        verify(server, 3, "/products/100", GET.toString());
    }

    @Test
    void shouldThrowExceptionOnErrorStatus() {
        server.enqueue(new MockResponse().setResponseCode(SERVICE_UNAVAILABLE.value()));

        assertThrowsWithCause(IOException.class, () -> http.get("/products/{id}", 100)
                .dispatch(status(),
                        on(OK).call(Product.class, product -> log.info("Product: " + product)),
                        on(NOT_FOUND).call(response -> log.warn("Product not found")),
                        anyStatus().call(pass()))
                .join());

        verify(server, 1, "/products/100", GET.toString());
    }

    @Test
    void shouldProcessNestedRouting() {
        server.enqueue(new MockResponse().setResponseCode(OK.value()));
        server.enqueue(new MockResponse().setResponseCode(NOT_FOUND.value()));
        server.enqueue(new MockResponse().setResponseCode(MOVED_PERMANENTLY.value()));

        for (int i = 0; i < 3; i++) {
            http.get("/products/{id}", 100)
                    .dispatch(series(),
                            on(SUCCESSFUL).call(Product.class, product -> log.info("Product: " + product)),
                            on(CLIENT_ERROR).dispatch(
                                    status(),
                                    on(NOT_FOUND).call(response -> log.warn("Product not found")),
                                    on(TOO_MANY_REQUESTS).call(response -> {
                                        throw new RuntimeException("Too many reservation requests");
                                    }),
                                    anyStatus().call(pass())),
                            on(SERVER_ERROR).call(response -> {
                                throw new RuntimeException("Server error");
                            }),
                            anySeries().call(pass()))
                    .join();
        }

        verify(server, 3, "/products/100", GET.toString());
    }

    @Test
    void shouldRethrowDefinedException() {
        server.enqueue(new MockResponse().setResponseCode(TOO_MANY_REQUESTS.value()));
        server.enqueue(new MockResponse().setResponseCode(SERVER_ERROR.value()));

        for (int i = 0; i < 2; i++) {
            assertThrowsWithCause(RuntimeException.class, () -> {
                http.get("/products/{id}", 100)
                        .dispatch(series(),
                                on(SUCCESSFUL).call(Product.class, product -> log.info("Product: " + product)),
                                on(CLIENT_ERROR).dispatch(
                                        status(),
                                        on(NOT_FOUND).call(response -> log.warn("Product not found")),
                                        on(TOO_MANY_REQUESTS).call(response -> {
                                            throw new RuntimeException("Too many reservation requests");
                                        }),
                                        anyStatus().call(pass())),
                                on(SERVER_ERROR).call(response -> {
                                    throw new RuntimeException("Server error");
                                }),
                                anySeries().call(pass()))
                        .join();
            });
        }

        verify(server, 2, "/products/100", GET.toString());
    }

}
