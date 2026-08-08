package org.dromara.fund.domain.dto;

import lombok.Data;

/** 历史 NAV 位置上游统一响应信封。 */
@Data
public class NavPositionProviderEnvelope {

    private boolean success;
    private NavPositionProviderResponse data;
    private ProviderError error;

    @Data
    public static class ProviderError {
        private String code;
        private String message;
    }
}
