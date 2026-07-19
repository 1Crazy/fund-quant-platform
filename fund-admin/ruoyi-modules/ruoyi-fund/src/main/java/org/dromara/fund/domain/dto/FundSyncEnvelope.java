package org.dromara.fund.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * fund-quant 批量同步载荷，外层仍由 QuantProviderEnvelope 包装。
 *
 * @param <T> 标准化记录类型
 */
@Data
public class FundSyncEnvelope<T> {

    private FundSyncBatchMetaDto meta;
    private List<T> records = List.of();
    private List<FundProviderQualityIssueDto> issues = List.of();
}
