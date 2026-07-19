package org.dromara.fund.client;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.config.FundEstimateProperties;
import org.dromara.fund.domain.dto.EstimateProviderEnvelope;
import org.dromara.fund.domain.dto.EstimateProviderResponse;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * 上游基金估值接口客户端。
 */
@Component
@RequiredArgsConstructor
public class FundEstimateProviderClient {

    private final FundEstimateProperties properties;
    private final RestTemplateBuilder restTemplateBuilder;

    public EstimateProviderResponse fetch(String fundCode) {
        if (properties.getProviderUrl() == null || properties.getProviderUrl().isBlank()) {
            throw new ServiceException("基金估值上游接口未配置");
        }
        RestTemplate restTemplate = restTemplateBuilder
            .connectTimeout(java.time.Duration.ofSeconds(1))
            .readTimeout(java.time.Duration.ofSeconds(3))
            .build();
        try {
            EstimateProviderEnvelope envelope = properties.getProviderUrl().contains("{code}")
                ? restTemplate.getForObject(properties.getProviderUrl(), EstimateProviderEnvelope.class, fundCode)
                : restTemplate.getForObject(
                    URI.create(appendCode(properties.getProviderUrl(), fundCode)),
                    EstimateProviderEnvelope.class
                );
            if (envelope == null) {
                throw new ServiceException("基金估值上游未返回数据");
            }
            if (!envelope.isSuccess() || envelope.getData() == null) {
                String message = envelope.getError() == null
                    ? "基金估值上游返回失败" : envelope.getError().getMessage();
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
}
