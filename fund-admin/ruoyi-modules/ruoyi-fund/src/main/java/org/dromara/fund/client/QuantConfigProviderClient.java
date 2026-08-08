package org.dromara.fund.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.config.QuantConfigProperties;
import org.dromara.fund.domain.dto.QuantConfigProviderValidationEnvelope;
import org.dromara.fund.domain.dto.QuantConfigProviderValidationRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Python 仅校验量化配置结构；发布和数据库写入仍由 Java 负责。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuantConfigProviderClient {
    private final QuantConfigProperties properties;
    private final ObjectMapper objectMapper;

    public void verify(QuantConfigProviderValidationRequest request) {
        if (properties.getProviderValidationUrl() == null || properties.getProviderValidationUrl().isBlank()) {
            throw new ServiceException("量化配置 Python 校验接口未配置");
        }
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.warn("量化配置 Python 校验请求序列化异常，type={}", e.getClass().getSimpleName());
            throw new ServiceException("量化配置 Python 校验请求序列化失败").setDetailMessage(e.getMessage());
        }
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(properties.getProviderConnectTimeout())
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        try {
            HttpRequest outboundRequest = HttpRequest.newBuilder(URI.create(properties.getProviderValidationUrl()))
                .timeout(properties.getProviderReadTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = client.send(outboundRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("量化配置 Python 校验响应异常，url={}, status={}",
                    properties.getProviderValidationUrl(), response.statusCode());
                throw new ServiceException("量化配置 Python 校验请求失败")
                    .setDetailMessage("HTTP " + response.statusCode());
            }
            QuantConfigProviderValidationEnvelope envelope = objectMapper.readValue(
                response.body(), QuantConfigProviderValidationEnvelope.class);
            if (envelope == null || !envelope.isSuccess() || envelope.getData() == null) {
                String message = envelope == null || envelope.getError() == null
                    ? "量化配置 Python 校验未返回结果" : envelope.getError().getMessage();
                throw new ServiceException(message);
            }
            if (!envelope.getData().isValid()) {
                List<String> errors = envelope.getData().getErrors();
                throw new ServiceException("量化配置 Python 校验失败: {}", errors == null ? "未提供错误详情" : String.join("; ", errors));
            }
        } catch (JsonProcessingException e) {
            log.warn("量化配置 Python 校验响应解析异常，type={}", e.getClass().getSimpleName());
            throw new ServiceException("量化配置 Python 校验响应解析失败").setDetailMessage(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("量化配置 Python 校验请求中断，url={}", properties.getProviderValidationUrl());
            throw new ServiceException("量化配置 Python 校验请求失败").setDetailMessage(e.getMessage());
        } catch (IOException e) {
            log.warn("量化配置 Python 校验请求异常，url={}, type={}",
                properties.getProviderValidationUrl(), e.getClass().getSimpleName());
            throw new ServiceException("量化配置 Python 校验请求失败").setDetailMessage(e.getMessage());
        }
    }
}
