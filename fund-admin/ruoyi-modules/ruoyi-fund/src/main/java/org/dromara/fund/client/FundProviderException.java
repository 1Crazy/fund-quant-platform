package org.dromara.fund.client;

import lombok.Getter;

/**
 * fund-quant 结构化错误，保留是否可重试，避免 Java 对字段漂移等确定性失败继续退避重放。
 */
@Getter
public class FundProviderException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;
    private final Integer retryAfterSeconds;

    public FundProviderException(String errorCode, String message, boolean retryable, Integer retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
