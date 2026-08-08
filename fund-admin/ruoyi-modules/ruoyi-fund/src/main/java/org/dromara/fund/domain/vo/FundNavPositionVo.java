package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 历史 NAV 位置视图。
 *
 * <p>区域表达历史净值所处位置，不构成内在价值判断或交易建议。</p>
 */
@Data
public class FundNavPositionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fundCode;
    private LocalDate tradeDate;
    private OffsetDateTime calculatedAt;
    private String status;
    private String algorithmVersion;
    private Long configReleaseVersion;
    private String configReleaseChecksum;
    private Long navPositionConfigVersion;
    private String navPositionConfigChecksum;
    private String inputDataVersion;
    private BigDecimal navPercentile;
    private BigDecimal currentDrawdown;
    private BigDecimal ma60Deviation;
    private BigDecimal ma120Deviation;
    private BigDecimal ma250Deviation;
    private BigDecimal navPositionScore;
    private String navPositionRegion;
    private Integer sampleCount;
    private LocalDate effectiveStartDate;
    private LocalDate effectiveEndDate;
    private List<NavPositionReasonVo> reasons = List.of();
    private List<NavPositionIndicatorVo> indicators = List.of();

    @Data
    public static class NavPositionReasonVo implements Serializable {
        private String code;
        private String message;
        private BigDecimal actual;
        private BigDecimal required;
    }

    @Data
    public static class NavPositionIndicatorVo implements Serializable {
        private String code;
        private BigDecimal value;
        private boolean available;
        private String reasonCode;
    }
}
