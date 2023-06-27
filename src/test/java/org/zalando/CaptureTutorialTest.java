package org.zalando;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.zalando.riptide.Http;
import org.zalando.riptide.capture.Capture;
import org.zalando.util.Product;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.OK;
import static org.zalando.util.MockWebServerUtil.getBaseUrl;
import static org.zalando.util.MockWebServerUtil.jsonMockResponse;
import static org.zalando.util.MockWebServerUtil.verify;
import static org.zalando.riptide.Bindings.anyStatus;
import static org.zalando.riptide.Bindings.on;
import static org.zalando.riptide.Navigators.status;

@Slf4j
class CaptureTutorialTest {
    private final MockWebServer server = new MockWebServer();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Http http = Http.builder()
            .executor(executor)
            .requestFactory(new SimpleClientHttpRequestFactory())
            .baseUrl(getBaseUrl(server))
            .converter(new MappingJackson2HttpMessageConverter(
                    new ObjectMapper().setSerializationInclusion(NON_ABSENT)))
            .build();

    @SneakyThrows
    @AfterEach
    void shutdown() {
        executor.shutdown();
        server.shutdown();
    }

    @Test
    void shouldCaptureRequestBody() {
        server.enqueue(jsonMockResponse("{\"id\":\"100\", \"name\":\"test_product\"}"));

        Capture<Product> capture = Capture.empty();
        Product product = http.get("/products/{id}", 100)
                .dispatch(status(),
                        on(OK).call(Product.class, capture),
                        anyStatus().call(response -> {
                            throw new RuntimeException("Invalid status");
                        }))
                .thenApply(capture)
                .join();

        assertEquals(100, product.getId());
        verify(server, 1, "/products/100", GET.toString());
    }

    @Test
    void shouldCaptureEmptyRequestBody() {
        server.enqueue(jsonMockResponse(""));

        Capture<Product> capture = Capture.empty();
        Product product = http.get("/products/{id}", 100)
                .dispatch(status(),
                        on(OK).call(Product.class, capture),
                        anyStatus().call(response -> {
                            throw new RuntimeException("Invalid status");
                        }))
                .thenApply(capture)
                .join();

        assertNull(product);
        verify(server, 1, "/products/100", GET.toString());
    }

}
