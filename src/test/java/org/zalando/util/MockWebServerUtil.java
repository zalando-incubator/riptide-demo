package org.zalando.util;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.util.concurrent.TimeUnit;

import static java.util.stream.IntStream.range;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

public class MockWebServerUtil {

    public static String getBaseUrl(MockWebServer server) {
        return String.format("http://%s:%s", server.getHostName(), server.getPort());
    }

    public static MockResponse emptyMockResponse() {
        return new MockResponse().setResponseCode(NO_CONTENT.value());
    }

    public static MockResponse jsonMockResponse(String body) {
        return new MockResponse().setResponseCode(OK.value())
                .setBody(body)
                .setHeader(CONTENT_TYPE, "application/json");
    }

    public static RecordedRequest getRecordedRequest(MockWebServer server) {
        try {
            return server.takeRequest(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void verify(MockWebServer server,
                              int expectedRequestsCount,
                              String expectedPath,
                              String expectedMethod) {
        range(0, expectedRequestsCount).forEach(i -> {
            RecordedRequest recordedRequest = getRecordedRequest(server);
            assertNotNull(recordedRequest);
            assertEquals(expectedPath, recordedRequest.getPath());
            assertEquals(expectedMethod, recordedRequest.getMethod());
        });
    }

}
