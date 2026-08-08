package org.dromara.fund.domain.dto;

import lombok.Data;

import java.util.Map;

/** 量化任务创建时固定的发布版本与配置分组血缘。 */
@Data
public class QuantConfigReleaseReference {
    private Long releaseVersion;
    private String releaseChecksum;
    private Map<String, GroupReference> groups;

    @Data
    public static class GroupReference {
        private Integer configVersion;
        private Integer schemaVersion;
        private String checksum;
    }
}
