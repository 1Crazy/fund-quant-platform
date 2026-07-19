package org.dromara.fund.domain.dto;

import lombok.Data;

/**
 * fund-quant 通用响应包装。
 *
 * @param <T> 业务数据类型
 */
@Data
public class QuantProviderEnvelope<T> {

    private boolean success;
    private T data;
    private ProviderError error;
    private String requestId;

    @Data
    public static class ProviderError {
        private String code;
        private String message;
        private boolean retryable;
    }
}
