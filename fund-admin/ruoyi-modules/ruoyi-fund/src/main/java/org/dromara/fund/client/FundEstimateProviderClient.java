package org.dromara.fund.client;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.config.FundEstimateProperties;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.domain.dto.EstimateProviderEnvelope;
import org.dromara.fund.domain.dto.EstimateProviderResponse;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * 上游基金估值接口客户端。
 */
@Component
@RequiredArgsConstructor
public class FundEstimateProviderClient {

    private static final String RELEASE_VERSION_HEADER = "X-Quant-Config-Release-Version";
    private static final String RELEASE_CHECKSUM_HEADER = "X-Quant-Config-Release-Checksum";
    private static final String RESULT_CACHE_SECONDS_HEADER = "X-Fund-Estimate-Result-Cache-Seconds";
    private static final String QUOTE_CACHE_SECONDS_HEADER = "X-Fund-Estimate-Quote-Cache-Seconds";

    private final FundEstimateProperties properties;
    private final FundEstimateRuntimeSettings runtimeSettings;
    private final RestTemplateBuilder restTemplateBuilder;

    public EstimateProviderResponse fetch(String fundCode, QuantConfigTaskContext configContext) {
        if (properties.getProviderUrl() == null || properties.getProviderUrl().isBlank()) {
            throw new ServiceException("基金估值上游接口未配置");
        }
        if (configContext == null || configContext.getConfigReleaseVersion() == null
            || configContext.getConfigReleaseChecksum() == null || configContext.getConfigReleaseChecksum().isBlank()) {
            throw new ServiceException("QUANT_CONFIG_VERSION_MISMATCH");
        }
        RestTemplate restTemplate = restTemplateBuilder
            .connectTimeout(runtimeSettings.getProviderConnectTimeout())
            .readTimeout(runtimeSettings.getProviderReadTimeout())
            .build();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(RELEASE_VERSION_HEADER, configContext.getConfigReleaseVersion().toString());
            headers.set(RELEASE_CHECKSUM_HEADER, configContext.getConfigReleaseChecksum());
            headers.set(RESULT_CACHE_SECONDS_HEADER, String.valueOf(runtimeSettings.getProviderResultCacheSeconds()));
            headers.set(QUOTE_CACHE_SECONDS_HEADER, String.valueOf(runtimeSettings.getMarketQuoteCacheSeconds()));
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<EstimateProviderEnvelope> response = properties.getProviderUrl().contains("{code}")
                ? restTemplate.exchange(
                    properties.getProviderUrl(), HttpMethod.GET, request, EstimateProviderEnvelope.class, fundCode)
                : restTemplate.exchange(
                    URI.create(appendCode(properties.getProviderUrl(), fundCode)),
                    HttpMethod.GET, request, EstimateProviderEnvelope.class);
            EstimateProviderEnvelope envelope = response.getBody();
            if (envelope == null) {
                throw new ServiceException("基金估值上游未返回数据");
            }
            if (!envelope.isSuccess() || envelope.getData() == null) {
                String message = envelope.getError() == null
                    ? "基金估值上游返回失败"
                    : quantConfigErrorCodeOrMessage(envelope.getError());
                throw new ServiceException(message);
            }
            return envelope.getData();
        } catch (RestClientException e) {
            throw new ServiceException("基金估值上游请求失败").setDetailMessage(e.getMessage());
        }
    }

    private String appendCode(String baseUrl, String fundCode) {
        return baseUrl.endsWith("/") ? baseUrl + fundCode : baseUrl + "/" + fundCode;
    }

    private String quantConfigErrorCodeOrMessage(EstimateProviderEnvelope.ProviderError error) {
        if (error.getCode() != null && error.getCode().startsWith("QUANT_CONFIG_")) {
            return error.getCode();
        }
        return error.getMessage();
    }
}
