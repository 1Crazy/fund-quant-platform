package org.dromara.fund.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.fund.domain.bo.QuantConfigDraftBo;
import org.dromara.fund.domain.bo.QuantConfigCloneBo;
import org.dromara.fund.domain.bo.QuantConfigReleaseBo;
import org.dromara.fund.domain.vo.QuantConfigDiffVo;
import org.dromara.fund.domain.vo.QuantConfigGroupVo;
import org.dromara.fund.domain.vo.QuantConfigReleaseVo;
import org.dromara.fund.domain.vo.QuantConfigVersionVo;
import org.dromara.fund.service.IQuantConfigService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 量化配置管理接口；计算端只读取发布版本，不接受前端传入的数学参数。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/fund/config")
public class QuantConfigController {
    private final IQuantConfigService configService;

    @SaCheckPermission("fund:config:list")
    @GetMapping("/groups")
    public R<List<QuantConfigGroupVo>> groups() {
        return R.ok(configService.listGroups());
    }

    @SaCheckPermission("fund:config:list")
    @GetMapping("/versions")
    public TableDataInfo<QuantConfigVersionVo> versions(
        @RequestParam(required = false) String configCode,
        @RequestParam(required = false) String status,
        PageQuery pageQuery
    ) {
        return configService.queryVersionPage(configCode, status, pageQuery);
    }

    @SaCheckPermission("fund:config:query")
    @GetMapping("/versions/{id}")
    public R<QuantConfigVersionVo> version(@PathVariable @Min(1) Long id) {
        return R.ok(configService.queryVersion(id));
    }

    @SaCheckPermission("fund:config:edit")
    @Log(title = "量化配置草稿", businessType = BusinessType.INSERT)
    @PostMapping("/drafts")
    public R<QuantConfigVersionVo> createDraft(@Valid @RequestBody QuantConfigDraftBo bo) {
        return R.ok(configService.createDraft(bo));
    }

    @SaCheckPermission("fund:config:edit")
    @Log(title = "量化配置草稿", businessType = BusinessType.INSERT)
    @PostMapping("/drafts/{id}/clone")
    public R<QuantConfigVersionVo> cloneDraft(
        @PathVariable @Min(1) Long id,
        @Valid @RequestBody(required = false) QuantConfigCloneBo bo
    ) {
        return R.ok(configService.cloneDraft(id, bo));
    }

    @SaCheckPermission("fund:config:edit")
    @Log(title = "量化配置草稿", businessType = BusinessType.UPDATE)
    @PutMapping("/drafts/{id}")
    public R<QuantConfigVersionVo> updateDraft(
        @PathVariable @Min(1) Long id,
        @Valid @RequestBody QuantConfigDraftBo bo
    ) {
        return R.ok(configService.updateDraft(id, bo));
    }

    @SaCheckPermission("fund:config:validate")
    @Log(title = "量化配置校验", businessType = BusinessType.UPDATE)
    @PostMapping("/drafts/{id}/validate")
    public R<QuantConfigVersionVo> validateDraft(
        @PathVariable @Min(1) Long id,
        @RequestParam @Min(0) Long revision
    ) {
        return R.ok(configService.validateDraft(id, revision));
    }

    @SaCheckPermission("fund:config:query")
    @GetMapping("/versions/diff")
    public R<QuantConfigDiffVo> diff(
        @RequestParam(required = false) @Min(1) Long baseId,
        @RequestParam @Min(1) Long targetId
    ) {
        return R.ok(configService.diff(baseId, targetId));
    }

    @SaCheckPermission("fund:config:list")
    @GetMapping("/releases")
    public R<List<QuantConfigReleaseVo>> releases() {
        return R.ok(configService.queryReleaseHistory());
    }

    @SaCheckPermission("fund:config:query")
    @GetMapping("/releases/{releaseVersion}")
    public R<QuantConfigReleaseVo> release(@PathVariable @Min(1) Long releaseVersion) {
        return R.ok(configService.queryRelease(releaseVersion));
    }

    @SaCheckPermission("fund:config:publish")
    @Log(title = "量化配置发布", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/releases")
    public R<QuantConfigReleaseVo> publish(@Valid @RequestBody QuantConfigReleaseBo bo) {
        return R.ok(configService.publish(bo));
    }

    @SaCheckPermission("fund:config:rollback")
    @Log(title = "量化配置回滚", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/releases/{releaseVersion}/rollback")
    public R<QuantConfigReleaseVo> rollback(
        @PathVariable @Min(1) Long releaseVersion,
        @Valid @RequestBody QuantConfigReleaseBo bo
    ) {
        return R.ok(configService.rollback(releaseVersion, bo));
    }
}
