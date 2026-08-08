package org.dromara.fund.domain.dto;

import lombok.Data;

import java.util.List;

/** Java 请求 Python 复核发布结构的内部协议。 */
@Data
public class QuantConfigProviderValidationRequest {
    private List<ConfigItem> configs;
    private String releaseChecksum;

    @Data
    public static class ConfigItem {
        private String configCode;
        private Integer configVersion;
        private Integer schemaVersion;
        private String configJson;
        private String checksum;
    }
}
