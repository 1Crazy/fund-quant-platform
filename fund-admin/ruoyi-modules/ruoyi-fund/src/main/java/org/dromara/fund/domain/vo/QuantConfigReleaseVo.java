package org.dromara.fund.domain.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 原子发布版本的管理端视图。 */
@Data
public class QuantConfigReleaseVo {
    private Long id;
    private Long releaseVersion;
    private String status;
    private String checksum;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime publishedAt;
    private Long rollbackOfReleaseVersion;
    private String changeSummary;
    private List<QuantConfigVersionVo> items;
}
