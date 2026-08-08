package org.dromara.fund.client;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.config.FundNavPositionProperties;
import org.dromara.fund.domain.dto.NavPositionProviderEnvelope;
import org.dromara.fund.domain.dto.NavPositionProviderResponse;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/** 历史 NAV 位置计算上游客户端。 */
@Component
@RequiredArgsConstructor
public class FundNavPositionProviderClient {

    private static final String RELEASE_VERSION_HEADER = "X-Quant-Config-Release-Version";
    private static final String RELEASE_CHECKSUM_HEADER = "X-Quant-Config-Release-Checksum";

    private final FundNavPositionProperties properties;
    private final FundEstimateRuntimeSettings runtimeSettings;
    private final RestTemplateBuilder restTemplateBuilder;

    public NavPositionProviderResponse fetch(String fundCode, QuantConfigTaskContext configContext) {
        if (properties.getProviderUrl() == null || properties.getProviderUrl().isBlank()) {
            throw new ServiceException("历史 NAV 位置上游接口未配置");
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
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<NavPositionProviderEnvelope> response = properties.getProviderUrl().contains("{code}")
                ? restTemplate.exchange(
                    properties.getProviderUrl(), HttpMethod.GET, request, NavPositionProviderEnvelope.class, fundCode)
                : restTemplate.exchange(
                    URI.create(appendCode(properties.getProviderUrl(), fundCode)),
                    HttpMethod.GET, request, NavPositionProviderEnvelope.class);
            NavPositionProviderEnvelope envelope = response.getBody();
            if (envelope == null) {
                throw new ServiceException("历史 NAV 位置上游未返回数据");
            }
            if (!envelope.isSuccess() || envelope.getData() == null) {
                NavPositionProviderEnvelope.ProviderError error = envelope.getError();
                String message = error == null ? "历史 NAV 位置上游返回失败"
                    : error.getCode() != null && error.getCode().startsWith("QUANT_CONFIG_")
                        ? error.getCode() : error.getMessage();
                throw new ServiceException(message);
            }
            return envelope.getData();
        } catch (RestClientException error) {
            throw new ServiceException("历史 NAV 位置上游请求失败").setDetailMessage(error.getMessage());
        }
    }

    private String appendCode(String baseUrl, String fundCode) {
        return baseUrl.endsWith("/") ? baseUrl + fundCode : baseUrl + "/" + fundCode;
    }
}
