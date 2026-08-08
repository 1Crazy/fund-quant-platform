package org.dromara.fund.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.config.QuantConfigProperties;
import org.dromara.fund.domain.dto.QuantConfigProviderValidationRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 发布前 Java/Python HTTP 契约：必须发送 JSON 且只接受明确的校验成功结果。 */
@Tag("dev")
final class QuantConfigProviderClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verifyMustSendTheExactReleasePayloadUsingHttp11() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestProtocol = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestProtocol.set(exchange.getProtocol());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            writeJson(exchange, 200, "{\"success\":true,\"data\":{\"valid\":true,\"errors\":[]},\"requestId\":\"request-1\"}");
        });
        try {
            new QuantConfigProviderClient(properties(server), objectMapper).verify(request());

            JsonNode payload = objectMapper.readTree(requestBody.get());
            assertEquals("f".repeat(64), payload.path("releaseChecksum").asText());
            assertEquals("ESTIMATE", payload.path("configs").get(0).path("configCode").asText());
            assertTrue(requestProtocol.get().startsWith("HTTP/1.1"));
            assertTrue(contentType.get().startsWith("application/json"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void verifyMustSurfaceProviderValidationErrorsAndNeverTreatThemAsSuccess() throws Exception {
        HttpServer server = startServer(exchange -> writeJson(exchange, 200,
            "{\"success\":true,\"data\":{\"valid\":false,\"errors\":[\"unsupported schema\"]}}"));
        try {
            ServiceException exception = assertThrows(ServiceException.class,
                () -> new QuantConfigProviderClient(properties(server), objectMapper).verify(request()));

            assertTrue(exception.getMessage().contains("量化配置 Python 校验失败"));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/v1/quant-config/validate", handler);
        server.start();
        return server;
    }

    private QuantConfigProperties properties(HttpServer server) {
        QuantConfigProperties properties = new QuantConfigProperties();
        properties.setProviderValidationUrl("http://localhost:" + server.getAddress().getPort()
            + "/internal/v1/quant-config/validate");
        properties.setProviderConnectTimeout(Duration.ofSeconds(2));
        properties.setProviderReadTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private QuantConfigProviderValidationRequest request() {
        QuantConfigProviderValidationRequest.ConfigItem item = new QuantConfigProviderValidationRequest.ConfigItem();
        item.setConfigCode("ESTIMATE");
        item.setConfigVersion(1);
        item.setSchemaVersion(1);
        item.setConfigJson("{\"max_quote_age_seconds\":90}");
        item.setChecksum("a".repeat(64));

        QuantConfigProviderValidationRequest request = new QuantConfigProviderValidationRequest();
        request.setConfigs(java.util.List.of(item));
        request.setReleaseChecksum("f".repeat(64));
        return request;
    }

    private void writeJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
