package org.dromara.fund.client;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.config.FundDataProperties;
import org.dromara.fund.domain.dto.FundNavProviderResponse;
import org.dromara.fund.domain.dto.FundHoldingProviderResponse;
import org.dromara.fund.domain.dto.HoldingQuoteProviderResponse;
import org.dromara.fund.domain.dto.FundProviderResponse;
import org.dromara.fund.domain.dto.QuantProviderEnvelope;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * fund-quant 基金基础数据客户端。
 */
@Component
@RequiredArgsConstructor
public class FundDataProviderClient {

    private final FundDataProperties properties;
    private final RestTemplateBuilder restTemplateBuilder;

    public FundProviderResponse fetchFund(String fundCode) {
        URI uri = URI.create(baseUrl() + "/internal/v1/data/fund/" + fundCode);
        ResponseEntity<QuantProviderEnvelope<FundProviderResponse>> response = exchange(
            uri,
            new ParameterizedTypeReference<>() {
            }
        );
        return requireData(response.getBody(), "基金基础信息");
    }

    public List<FundNavProviderResponse> fetchNav(String fundCode, int days) {
        URI uri = URI.create(baseUrl() + "/internal/v1/data/nav/" + fundCode + "?days=" + days);
        ResponseEntity<QuantProviderEnvelope<List<FundNavProviderResponse>>> response = exchange(
            uri,
            new ParameterizedTypeReference<>() {
            }
        );
        return requireData(response.getBody(), "基金历史净值");
    }

    public List<FundHoldingProviderResponse> fetchHoldings(String fundCode) {
        URI uri = URI.create(baseUrl() + "/internal/v1/data/holdings/" + fundCode);
        ResponseEntity<QuantProviderEnvelope<List<FundHoldingProviderResponse>>> response = exchange(
            uri,
            new ParameterizedTypeReference<>() {
            }
        );
        return requireData(response.getBody(), "基金股票持仓");
    }

    public List<HoldingQuoteProviderResponse> fetchHoldingQuotes(String fundCode) {
        URI uri = URI.create(baseUrl() + "/internal/v1/data/holding-quotes/" + fundCode);
        ResponseEntity<QuantProviderEnvelope<List<HoldingQuoteProviderResponse>>> response = exchange(
            uri,
            new ParameterizedTypeReference<>() {
            }
        );
        return requireData(response.getBody(), "基金持仓实时行情");
    }

    public List<FundProviderResponse> searchFunds(String keyword, int limit) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl() + "/internal/v1/data/funds")
            .queryParam("keyword", keyword)
            .queryParam("limit", limit)
            .build()
            .encode()
            .toUri();
        ResponseEntity<QuantProviderEnvelope<List<FundProviderResponse>>> response = exchange(
            uri,
            new ParameterizedTypeReference<>() {
            }
        );
        return requireData(response.getBody(), "基金搜索结果");
    }

    public org.dromara.fund.domain.dto.FundSyncEnvelope<FundProviderResponse> syncCatalog(String batchId, int page, int pageSize) {
        URI uri = URI.create(baseUrl() + "/internal/v1/data/sync/catalog");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<QuantProviderEnvelope<org.dromara.fund.domain.dto.FundSyncEnvelope<FundProviderResponse>>> response = exchange(
            uri,
            HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "batchId", batchId,
                "page", page,
                "pageSize", pageSize
            ), headers),
            new ParameterizedTypeReference<>() {
            }
        );
        return requireData(response.getBody(), "基金目录同步结果");
    }

    public org.dromara.fund.domain.dto.FundSyncEnvelope<FundProviderResponse> syncFund(String fundCode, String batchId) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl() + "/internal/v1/data/sync/fund/" + fundCode)
            .queryParam("batchId", batchId)
            .build()
            .encode()
            .toUri();
        ResponseEntity<QuantProviderEnvelope<org.dromara.fund.domain.dto.FundSyncEnvelope<FundProviderResponse>>> response = exchange(
            uri,
            HttpMethod.POST,
            HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {
            }
        );
        return requireData(response.getBody(), "基金档案同步结果");
    }

    public org.dromara.fund.domain.dto.FundSyncEnvelope<FundNavProviderResponse> syncNav(
        String fundCode,
        LocalDate startDate,
        LocalDate endDate,
        String batchId
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString(baseUrl() + "/internal/v1/data/sync/nav/" + fundCode)
            .queryParam("batchId", batchId);
        if (startDate != null) {
            builder.queryParam("startDate", startDate);
        }
        if (endDate != null) {
            builder.queryParam("endDate", endDate);
        }
        ResponseEntity<QuantProviderEnvelope<org.dromara.fund.domain.dto.FundSyncEnvelope<FundNavProviderResponse>>> response = exchange(
            builder.build().encode().toUri(),
            HttpMethod.POST,
            HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {
            }
        );
        return requireData(response.getBody(), "基金净值同步结果");
    }

    public org.dromara.fund.domain.dto.FundSyncEnvelope<FundHoldingProviderResponse> syncHoldings(
        String fundCode,
        LocalDate reportDate,
        String batchId
    ) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString(baseUrl() + "/internal/v1/data/sync/holdings/" + fundCode)
            .queryParam("batchId", batchId);
        if (reportDate != null) {
            builder.queryParam("reportDate", reportDate);
        }
        ResponseEntity<QuantProviderEnvelope<org.dromara.fund.domain.dto.FundSyncEnvelope<FundHoldingProviderResponse>>> response = exchange(
            builder.build().encode().toUri(),
            HttpMethod.POST,
            HttpEntity.EMPTY,
            new ParameterizedTypeReference<>() {
            }
        );
        return requireData(response.getBody(), "基金持仓同步结果");
    }

    private <T> ResponseEntity<T> exchange(URI uri, ParameterizedTypeReference<T> responseType) {
        return exchange(uri, HttpMethod.GET, null, responseType);
    }

    private <T> ResponseEntity<T> exchange(
        URI uri,
        HttpMethod method,
        HttpEntity<?> requestEntity,
        ParameterizedTypeReference<T> responseType
    ) {
        try {
            return restTemplateBuilder
                // fund-quant 的本地 Uvicorn 端点只提供 HTTP/1.1，避免 JDK 客户端尝试 h2/h2c 协商。
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .connectTimeout(properties.getProviderConnectTimeout())
                .readTimeout(properties.getProviderReadTimeout())
                .build()
                .exchange(uri, method, requestEntity, responseType);
        } catch (RestClientException e) {
            throw new ServiceException("基金数据中心请求失败").setDetailMessage(e.getMessage());
        }
    }

    private <T> T requireData(QuantProviderEnvelope<T> envelope, String dataName) {
        if (envelope == null) {
            throw new ServiceException("基金数据中心未返回{}", dataName);
        }
        if (!envelope.isSuccess() || envelope.getData() == null) {
            QuantProviderEnvelope.ProviderError error = envelope.getError();
            if (error == null) {
                throw new FundProviderException("EMPTY_PROVIDER_ENVELOPE", "基金数据中心返回失败", true, null);
            }
            throw new FundProviderException(
                error.getCode(),
                error.getMessage(),
                error.isRetryable(),
                error.getRetryAfterSeconds()
            );
        }
        return envelope.getData();
    }

    private String baseUrl() {
        String baseUrl = properties.getProviderBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ServiceException("基金数据中心地址未配置");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
