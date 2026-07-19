package org.dromara.fund.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.fund.domain.bo.FundQueryBo;
import org.dromara.fund.domain.vo.FundDetailVo;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.dromara.fund.domain.vo.FundListVo;
import org.dromara.fund.service.IFundEstimateService;
import org.dromara.fund.service.IFundQueryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
