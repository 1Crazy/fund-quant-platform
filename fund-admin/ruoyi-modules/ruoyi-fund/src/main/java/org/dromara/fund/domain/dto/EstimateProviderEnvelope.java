package org.dromara.fund.domain.dto;

import lombok.Data;

/**
 * 量化服务统一响应。
 */
@Data
public class EstimateProviderEnvelope {

    private boolean success;
    private EstimateProviderResponse data;
    private ProviderError error;
    private String requestId;

    @Data
    public static class ProviderError {
        private String code;
        private String message;
        private boolean retryable;
    }
}
