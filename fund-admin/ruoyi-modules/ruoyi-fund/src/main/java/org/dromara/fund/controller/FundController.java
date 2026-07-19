package org.dromara.fund.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.fund.domain.bo.FundDataQualityIssueQueryBo;
import org.dromara.fund.domain.bo.FundManualSyncBo;
import org.dromara.fund.domain.bo.FundQueryBo;
import org.dromara.fund.domain.bo.FundSyncRunQueryBo;
import org.dromara.fund.domain.dto.FundSyncStatusSummaryVo;
import org.dromara.fund.domain.vo.FundDataQualityIssueVo;
import org.dromara.fund.domain.vo.FundDetailVo;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.dromara.fund.domain.vo.FundHoldingVo;
import org.dromara.fund.domain.vo.FundListVo;
import org.dromara.fund.domain.vo.FundSyncRunVo;
import org.dromara.fund.service.IFundDataSyncService;
import org.dromara.fund.service.IFundEstimateService;
import org.dromara.fund.service.IFundQueryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 基金查询与实时估值接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/fund")
public class FundController {

    private final IFundQueryService fundQueryService;
    private final IFundEstimateService fundEstimateService;
    private final IFundDataSyncService fundDataSyncService;

    @SaCheckPermission("fund:info:list")
    @GetMapping("/list")
    public TableDataInfo<FundListVo> list(FundQueryBo bo, PageQuery pageQuery) {
        return fundQueryService.queryPage(bo, pageQuery);
    }

    @SaCheckPermission("fund:info:query")
    @GetMapping("/detail/{code}")
    public R<FundDetailVo> detail(
        @Pattern(regexp = "^[0-9A-Za-z]{1,12}$", message = "基金代码格式不正确")
        @PathVariable String code,
        @RequestParam(defaultValue = "3m")
        @Pattern(regexp = "^(1m|3m|6m|1y|3y|5y|all)$", message = "净值周期格式不正确")
        String period
    ) {
        return R.ok(fundQueryService.queryDetail(code, period));
    }

    @SaCheckPermission("fund:info:query")
    @GetMapping("/estimate/{code}")
    public R<FundEstimateVo> estimate(
        @Pattern(regexp = "^[0-9A-Za-z]{1,12}$", message = "基金代码格式不正确")
        @PathVariable String code
    ) {
        return R.ok(fundEstimateService.queryEstimate(code));
    }

    @SaCheckPermission("fund:info:query")
    @GetMapping("/holding/{code}")
    public R<List<FundHoldingVo>> holding(
        @Pattern(regexp = "^[0-9]{6}$", message = "基金代码格式不正确")
        @PathVariable String code,
        @RequestParam(required = false) LocalDate reportDate
    ) {
        return R.ok(fundDataSyncService.queryLatestHoldings(code, reportDate));
    }

    @SaCheckPermission("fund:sync:list")
    @GetMapping({"/sync/runs", "/sync/list"})
    public TableDataInfo<FundSyncRunVo> syncRuns(FundSyncRunQueryBo bo, PageQuery pageQuery) {
        return fundDataSyncService.queryRunPage(bo, pageQuery);
    }

    @SaCheckPermission("fund:sync:query")
    @GetMapping({"/sync/runs/{id}", "/sync/{id}"})
    public R<FundSyncRunVo> syncRunDetail(@PathVariable Long id) {
        return R.ok(fundDataSyncService.queryRunDetail(id));
    }

    @SaCheckPermission("fund:sync:query")
    @GetMapping({"/sync/status", "/sync/current"})
    public R<FundSyncStatusSummaryVo> syncStatus(
        @RequestParam(defaultValue = "FUND_INFO") String dataset,
        @RequestParam(required = false) String scopeType,
        @RequestParam(required = false) String scopeValue
    ) {
        return R.ok(fundDataSyncService.queryStatus(dataset, scopeType, scopeValue));
    }

    @SaCheckPermission("fund:sync:query")
    @GetMapping({"/sync/issues", "/quality-issues"})
    public TableDataInfo<FundDataQualityIssueVo> qualityIssues(
        FundDataQualityIssueQueryBo bo,
        PageQuery pageQuery
    ) {
        return fundDataSyncService.queryIssuePage(bo, pageQuery);
    }

    @SaCheckPermission("fund:sync:trigger")
    @PostMapping("/sync/trigger")
    public R<FundSyncRunVo> trigger(@Validated @RequestBody FundManualSyncBo bo) {
        String syncType = bo.getSyncType() == null ? "" : bo.getSyncType();
        if ("FULL_INIT".equalsIgnoreCase(syncType)
            || "ALL".equalsIgnoreCase(bo.getSyncScope())
            || "GLOBAL".equalsIgnoreCase(bo.getSyncScope())) {
            return R.ok(fundDataSyncService.runFullInitPartition(null));
        }
        if ("INCREMENTAL".equalsIgnoreCase(syncType)) {
            return R.ok(fundDataSyncService.runIncremental());
        }
        if (bo.getRangeStartDate() != null || bo.getRangeEndDate() != null) {
            return R.ok(fundDataSyncService.triggerFundSync(
                bo.getFundCode(),
                bo.getRangeStartDate(),
                bo.getRangeEndDate()
            ));
        }
        return R.ok(fundDataSyncService.triggerFundSync(bo.getFundCode(), bo.getDays() == null ? 366 : bo.getDays()));
    }

    @SaCheckPermission("fund:sync:retry")
    @PostMapping("/sync/runs/{id}/retry")
    public R<FundSyncRunVo> retry(@PathVariable Long id) {
        return R.ok(fundDataSyncService.retryRun(id));
    }
}
