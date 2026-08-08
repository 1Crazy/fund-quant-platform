package org.dromara.fund.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/** 量化配置版本的管理端视图。 */
@Data
public class QuantConfigVersionVo {
    private Long id;
    private String configCode;
    private Integer configVersion;
    private Integer schemaVersion;
    private String status;
    private String configJson;
    private String canonicalJson;
    private String checksum;
    private OffsetDateTime effectiveFrom;
    private Long revision;
    private String remark;
    private OffsetDateTime createTime;
    private OffsetDateTime updateTime;
}
