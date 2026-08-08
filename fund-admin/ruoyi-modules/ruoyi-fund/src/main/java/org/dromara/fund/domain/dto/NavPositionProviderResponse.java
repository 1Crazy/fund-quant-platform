package org.dromara.fund.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** fund-quant 返回的历史 NAV 位置结果。 */
@Data
public class NavPositionProviderResponse {

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
    private List<NavPositionReasonResponse> reasons = List.of();
    private List<NavPositionIndicatorResponse> indicators = List.of();

    @Data
    public static class NavPositionReasonResponse {
        private String code;
        private String message;
        private BigDecimal actual;
        private BigDecimal required;
    }

    @Data
    public static class NavPositionIndicatorResponse {
        private String code;
        private BigDecimal value;
        private boolean available;
        private String reasonCode;
    }
}
