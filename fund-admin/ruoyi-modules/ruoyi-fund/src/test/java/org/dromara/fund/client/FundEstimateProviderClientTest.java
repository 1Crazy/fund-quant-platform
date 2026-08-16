package org.dromara.fund.client;

import com.sun.net.httpserver.HttpServer;
import org.dromara.fund.config.FundEstimateProperties;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.domain.dto.EstimateProviderResponse;
import org.dromara.fund.domain.dto.QuantConfigReleaseReference;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Java/Python 实时估值 HTTP 响应契约。 */
@Tag("dev")
final class FundEstimateProviderClientTest {

    @Test
    void fetchParsesPartialEstimateAndPreservesExactReleaseLineage() throws Exception {
        AtomicReference<String> releaseHeader = new AtomicReference<>();
        AtomicReference<String> checksumHeader = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            releaseHeader.set(exchange.getRequestHeaders().getFirst("X-Quant-Config-Release-Version"));
            checksumHeader.set(exchange.getRequestHeaders().getFirst("X-Quant-Config-Release-Checksum"));
            writeJson(exchange, 200, """
                {"success":true,"data":{"fundCode":"000001","sourceStatus":"PARTIAL",
                "statusReason":"INSUFFICIENT_QUOTE_COVERAGE","holdingCoverageRate":78.5000,
                "quoteCoverageRate":55.0000,"missingQuoteCount":2,
                "estimateTime":"2026-08-16T10:00:00+08:00","quoteTime":"2026-08-16T09:59:30+08:00",
                "holdingReportDate":"2026-06-30","reportPeriod":"2026Q2","inputDataVersion":"nav-v3",
                "algorithmVersion":"holding-estimate-v2","tradeDate":"2026-08-16",
                "configReleaseVersion":2,"configReleaseChecksum":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "estimateConfigVersion":15,"estimateConfigChecksum":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}}
                """);
        });
        try {
            FundEstimateRuntimeSettings runtimeSettings = mock(FundEstimateRuntimeSettings.class);
            when(runtimeSettings.getProviderConnectTimeout()).thenReturn(Duration.ofSeconds(2));
            when(runtimeSettings.getProviderReadTimeout()).thenReturn(Duration.ofSeconds(2));
            when(runtimeSettings.getProviderResultCacheSeconds()).thenReturn(15);
            when(runtimeSettings.getMarketQuoteCacheSeconds()).thenReturn(15);
            FundEstimateProperties properties = new FundEstimateProperties();
            properties.setProviderUrl("http://localhost:" + server.getAddress().getPort()
                + "/internal/v1/data/estimate/{code}");

            EstimateProviderResponse response = new FundEstimateProviderClient(
                properties, runtimeSettings, new RestTemplateBuilder()
            ).fetch("000001", context());

            assertEquals("000001", response.getFundCode());
            assertEquals("PARTIAL", response.getSourceStatus());
            assertEquals("INSUFFICIENT_QUOTE_COVERAGE", response.getStatusReason());
            assertEquals("78.5000", response.getHoldingCoverageRate().toPlainString());
            assertEquals("55.0000", response.getQuoteCoverageRate().toPlainString());
            assertEquals(2, response.getMissingQuoteCount());
            assertEquals(2L, response.getConfigReleaseVersion());
            assertEquals(15L, response.getEstimateConfigVersion());
            assertEquals("2", releaseHeader.get());
            assertEquals("a".repeat(64), checksumHeader.get());
            assertTrue(response.getEstimateTime().isAfter(response.getQuoteTime()));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/v1/data/estimate/000001", handler);
        server.start();
        return server;
    }

    private QuantConfigTaskContext context() {
        QuantConfigReleaseReference.GroupReference estimateGroup = new QuantConfigReleaseReference.GroupReference();
        estimateGroup.setConfigVersion(15);
        estimateGroup.setSchemaVersion(2);
        estimateGroup.setChecksum("b".repeat(64));
        QuantConfigTaskContext context = new QuantConfigTaskContext();
        context.setConfigReleaseVersion(2L);
        context.setConfigReleaseChecksum("a".repeat(64));
        context.setGroups(Map.of("ESTIMATE", estimateGroup));
        return context;
    }

    private void writeJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}
