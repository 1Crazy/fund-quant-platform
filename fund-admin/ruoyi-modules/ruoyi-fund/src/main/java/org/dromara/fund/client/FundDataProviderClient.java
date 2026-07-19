package org.dromara.fund.client;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.config.FundDataProperties;
import org.dromara.fund.domain.dto.FundNavProviderResponse;
import org.dromara.fund.domain.dto.FundHoldingProviderResponse;
import org.dromara.fund.domain.dto.FundProviderResponse;
import org.dromara.fund.domain.dto.QuantProviderEnvelope;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

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

    private <T> ResponseEntity<T> exchange(URI uri, ParameterizedTypeReference<T> responseType) {
        try {
            return restTemplateBuilder
                .connectTimeout(properties.getProviderConnectTimeout())
                .readTimeout(properties.getProviderReadTimeout())
                .build()
                .exchange(uri, HttpMethod.GET, null, responseType);
        } catch (RestClientException e) {
            throw new ServiceException("基金数据中心请求失败").setDetailMessage(e.getMessage());
        }
    }

    private <T> T requireData(QuantProviderEnvelope<T> envelope, String dataName) {
        if (envelope == null) {
            throw new ServiceException("基金数据中心未返回{}", dataName);
        }
        if (!envelope.isSuccess() || envelope.getData() == null) {
            String message = envelope.getError() == null
                ? "基金数据中心返回失败" : envelope.getError().getMessage();
            throw new ServiceException(message);
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
