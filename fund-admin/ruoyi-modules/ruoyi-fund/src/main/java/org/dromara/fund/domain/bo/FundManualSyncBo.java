package org.dromara.fund.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

import java.time.LocalDate;

/**
 * 手动同步请求。
 */
@Data
public class FundManualSyncBo {

    @Pattern(regexp = "^$|^[0-9]{6}$", message = "基金代码格式不正确")
    private String fundCode;

    private String dataset;

    private String syncType;

    private String syncScope;

    private LocalDate rangeStartDate;

    private LocalDate rangeEndDate;

    @Min(value = 0, message = "净值天数必须大于等于 0")
    @Max(value = 5000, message = "净值天数必须小于等于 5000")
    private Integer days = 366;

    /** 日期范围必须按升序传入，避免上游把无效区间解释为全量同步。 */
    @AssertTrue(message = "开始日期不能晚于结束日期")
    public boolean isDateRangeValid() {
        return rangeStartDate == null || rangeEndDate == null || !rangeStartDate.isAfter(rangeEndDate);
    }
}
