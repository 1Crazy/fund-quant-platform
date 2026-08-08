package org.dromara.fund.domain.dto;

import lombok.Data;

import java.util.List;

/** Python 配置兼容性校验响应的最小包装。 */
@Data
public class QuantConfigProviderValidationEnvelope {
    private boolean success;
    private ValidationData data;
    private ProviderError error;
    private String requestId;

    @Data
    public static class ValidationData {
        private boolean valid;
        private List<String> errors;
    }

    @Data
    public static class ProviderError {
        private String code;
        private String message;
    }
}
