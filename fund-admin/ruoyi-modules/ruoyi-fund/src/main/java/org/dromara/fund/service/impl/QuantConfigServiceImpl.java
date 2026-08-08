package org.dromara.fund.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.fund.client.QuantConfigProviderClient;
import org.dromara.fund.config.QuantConfigProperties;
import org.dromara.fund.constant.QuantConfigCacheConstants;
import org.dromara.fund.domain.QuantConfigRelease;
import org.dromara.fund.domain.QuantConfigReleaseItem;
import org.dromara.fund.domain.QuantConfigVersion;
import org.dromara.fund.domain.bo.QuantConfigDraftBo;
import org.dromara.fund.domain.bo.QuantConfigCloneBo;
import org.dromara.fund.domain.bo.QuantConfigReleaseBo;
import org.dromara.fund.domain.dto.QuantConfigProviderValidationRequest;
import org.dromara.fund.domain.dto.QuantConfigReleaseReference;
import org.dromara.fund.domain.enums.QuantConfigCodeEnum;
import org.dromara.fund.domain.enums.QuantConfigVersionStatusEnum;
import org.dromara.fund.domain.vo.QuantConfigReleaseVo;
import org.dromara.fund.domain.vo.QuantConfigDiffEntryVo;
import org.dromara.fund.domain.vo.QuantConfigDiffVo;
import org.dromara.fund.domain.vo.QuantConfigGroupVo;
import org.dromara.fund.domain.vo.QuantConfigVersionVo;
import org.dromara.fund.mapper.QuantConfigReleaseMapper;
import org.dromara.fund.mapper.QuantConfigVersionMapper;
import org.dromara.fund.service.IQuantConfigService;
import org.dromara.fund.service.QuantConfigJsonSupport;
import org.dromara.fund.service.QuantConfigValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** PostgreSQL 为事实来源；Redis 仅投影已提交的发布版本。 */
@Service
@RequiredArgsConstructor
public class QuantConfigServiceImpl implements IQuantConfigService {
    private final QuantConfigVersionMapper versionMapper;
    private final QuantConfigReleaseMapper releaseMapper;
    private final QuantConfigJsonSupport jsonSupport;
    private final QuantConfigValidator validator;
    private final QuantConfigProviderClient providerClient;
    private final QuantConfigProperties properties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public List<QuantConfigGroupVo> listGroups() {
        return List.of(QuantConfigCodeEnum.values()).stream().map(code -> {
            QuantConfigGroupVo group = new QuantConfigGroupVo();
            group.setConfigCode(code.getCode());
            group.setDisplayName(code.getDescription());
            group.setSchemaVersion(QuantConfigCodeEnum.ESTIMATE == code ? 2 : 1);
            return group;
        }).toList();
    }

    @Override
    public TableDataInfo<QuantConfigVersionVo> queryVersionPage(String configCode, String status, PageQuery pageQuery) {
        if (configCode != null && !configCode.isBlank() && !QuantConfigCodeEnum.supports(configCode)) {
            throw new ServiceException("不支持的配置分组: {}", configCode);
        }
        if (status != null && !status.isBlank() && !QuantConfigVersionStatusEnum.supports(status)) {
            throw new ServiceException("不支持的配置版本状态: {}", status);
        }
        Page<QuantConfigVersionVo> page = versionMapper.selectVersionPage(pageQuery.build(), configCode, status);
        for (QuantConfigVersionVo item : page.getRecords()) {
            item.setCanonicalJson(jsonSupport.canonicalize(jsonSupport.readObject(item.getConfigJson())));
        }
        return TableDataInfo.build(page);
    }

    @Override
    public QuantConfigVersionVo queryVersion(Long id) {
        QuantConfigVersionVo item = versionMapper.selectVersionVo(id);
        if (item == null) {
            throw new ServiceException("量化配置版本不存在");
        }
        item.setCanonicalJson(jsonSupport.canonicalize(jsonSupport.readObject(item.getConfigJson())));
        return item;
    }

    @Override
    public QuantConfigVersionVo createDraft(QuantConfigDraftBo bo) {
        String canonicalJson = jsonSupport.canonicalize(jsonSupport.readObject(bo.getConfigJson()));
        QuantConfigVersion item = new QuantConfigVersion();
        item.setId(IdGeneratorUtil.nextLongId());
        item.setConfigCode(bo.getConfigCode());
        item.setConfigVersion(versionMapper.selectNextVersion(bo.getConfigCode()));
        item.setSchemaVersion(bo.getSchemaVersion());
        item.setStatus(QuantConfigVersionStatusEnum.DRAFT.getCode());
        item.setConfigJson(canonicalJson);
        item.setChecksum(jsonSupport.checksum(canonicalJson));
        item.setRevision(0L);
        item.setRemark(bo.getRemark());
        if (versionMapper.insert(item) != 1) {
            throw new ServiceException("创建量化配置草稿失败");
        }
        return queryVersion(item.getId());
    }

    @Override
    public QuantConfigVersionVo cloneDraft(Long sourceId, QuantConfigCloneBo bo) {
        QuantConfigVersion source = versionMapper.selectById(sourceId);
        if (source == null) {
            throw new ServiceException("量化配置版本不存在");
        }
        QuantConfigDraftBo draft = new QuantConfigDraftBo();
        draft.setConfigCode(source.getConfigCode());
        draft.setSchemaVersion(source.getSchemaVersion());
        draft.setConfigJson(source.getConfigJson());
        draft.setRemark(bo == null ? source.getRemark() : bo.getRemark());
        return createDraft(draft);
    }

    @Override
    public QuantConfigVersionVo updateDraft(Long id, QuantConfigDraftBo bo) {
        QuantConfigVersion existing = versionMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException("量化配置版本不存在");
        }
        if (!QuantConfigVersionStatusEnum.DRAFT.getCode().equals(existing.getStatus())) {
            throw new ServiceException("仅草稿版本允许编辑");
        }
        if (!Objects.equals(existing.getConfigCode(), bo.getConfigCode())) {
            throw new ServiceException("配置分组不可在编辑时变更");
        }
        String canonicalJson = jsonSupport.canonicalize(jsonSupport.readObject(bo.getConfigJson()));
        existing.setSchemaVersion(bo.getSchemaVersion());
        existing.setConfigJson(canonicalJson);
        existing.setChecksum(jsonSupport.checksum(canonicalJson));
        existing.setRemark(bo.getRemark());
        if (versionMapper.updateDraft(existing, bo.getRevision()) != 1) {
            throw new ServiceException("草稿已被其他操作更新，请刷新后重试");
        }
        return queryVersion(id);
    }

    @Override
    public QuantConfigVersionVo validateDraft(Long id, Long revision) {
        QuantConfigVersion item = versionMapper.selectById(id);
        if (item == null || !QuantConfigVersionStatusEnum.DRAFT.getCode().equals(item.getStatus())) {
            throw new ServiceException("仅草稿版本可以校验");
        }
        List<String> errors = validator.validate(item.getConfigCode(), item.getSchemaVersion(), jsonSupport.readObject(item.getConfigJson()));
        if (!errors.isEmpty()) {
            throw new ServiceException("量化配置校验失败: {}", String.join("; ", errors));
        }
        if (versionMapper.markValidated(id, revision) != 1) {
            throw new ServiceException("草稿已被其他操作更新，请刷新后重试");
        }
        return queryVersion(id);
    }

    @Override
    public QuantConfigDiffVo diff(Long baseId, Long targetId) {
        QuantConfigVersionVo target = queryVersion(targetId);
        QuantConfigVersionVo base = baseId == null ? null : queryVersion(baseId);
        if (base != null && !Objects.equals(base.getConfigCode(), target.getConfigCode())) {
            throw new ServiceException("仅支持比较同一配置分组的版本");
        }
        Map<String, String> before = base == null ? Map.of() : flattenJson(jsonSupport.readObject(base.getConfigJson()));
        Map<String, String> after = flattenJson(jsonSupport.readObject(target.getConfigJson()));
        Set<String> paths = new TreeSet<>();
        paths.addAll(before.keySet());
        paths.addAll(after.keySet());
        List<QuantConfigDiffEntryVo> changes = new ArrayList<>();
        for (String path : paths) {
            String beforeValue = before.get(path);
            String afterValue = after.get(path);
            if (Objects.equals(beforeValue, afterValue)) {
                continue;
            }
            QuantConfigDiffEntryVo entry = new QuantConfigDiffEntryVo();
            entry.setFieldPath(path);
            entry.setBefore(beforeValue);
            entry.setAfter(afterValue);
            entry.setType(beforeValue == null ? "ADDED" : afterValue == null ? "REMOVED" : "CHANGED");
            changes.add(entry);
        }
        QuantConfigDiffVo result = new QuantConfigDiffVo();
        result.setBaseId(baseId);
        result.setTargetId(targetId);
        result.setBaseChecksum(base == null ? null : base.getChecksum());
        result.setTargetChecksum(target.getChecksum());
        result.setChanges(changes);
        return result;
    }

    @Override
    public List<QuantConfigReleaseVo> queryReleaseHistory() {
        List<QuantConfigReleaseVo> releases = releaseMapper.selectReleaseHistory();
        for (QuantConfigReleaseVo release : releases) {
            release.setItems(releaseMapper.selectReleaseItems(release.getReleaseVersion()));
        }
        return releases;
    }

    @Override
    public QuantConfigReleaseVo queryRelease(Long releaseVersion) {
        QuantConfigReleaseVo release = releaseMapper.selectReleaseVo(releaseVersion);
        if (release == null) {
            throw new ServiceException("量化配置发布版本不存在");
        }
        release.setItems(releaseMapper.selectReleaseItems(releaseVersion));
        return release;
    }

    @Override
    public QuantConfigReleaseVo publish(QuantConfigReleaseBo bo) {
        if (releaseMapper.selectCount(null) == 0 && !properties.isInitialReleaseApproved()) {
            throw new ServiceException("D-011 初始量化口径尚未确认，禁止发布首个量化配置版本");
        }
        ReleasePlan plan = preparePlan(bo.getConfigVersionIds(), false);
        providerClient.verify(toProviderRequest(plan));
        QuantConfigReleaseVo release = transactionTemplate.execute(status -> persistRelease(plan, bo, null));
        if (release == null) {
            throw new ServiceException("量化配置发布事务未返回结果");
        }
        projectRelease(release);
        return release;
    }

    @Override
    public QuantConfigReleaseVo rollback(Long releaseVersion, QuantConfigReleaseBo bo) {
        QuantConfigReleaseVo source = queryRelease(releaseVersion);
        List<Long> versionIds = source.getItems().stream().map(QuantConfigVersionVo::getId).toList();
        ReleasePlan plan = preparePlan(versionIds, false);
        providerClient.verify(toProviderRequest(plan));
        QuantConfigReleaseVo release = transactionTemplate.execute(status -> persistRelease(plan, bo, releaseVersion));
        if (release == null) {
            throw new ServiceException("量化配置回滚事务未返回结果");
        }
        projectRelease(release);
        return release;
    }

    @Override
    public QuantConfigReleaseReference resolveActiveRelease() {
        QuantConfigRelease active = releaseMapper.selectActiveRelease();
        if (active == null) {
            throw new ServiceException("QUANT_CONFIG_NOT_PUBLISHED");
        }
        QuantConfigReleaseVo release = queryRelease(active.getReleaseVersion());
        cacheRelease(release, true);
        return toReleaseReference(release);
    }

    @Override
    public QuantConfigReleaseReference resolveRelease(Long releaseVersion) {
        QuantConfigReleaseVo release = queryRelease(releaseVersion);
        if (!"PUBLISHED".equals(release.getStatus())) {
            throw new ServiceException("QUANT_CONFIG_NOT_PUBLISHED");
        }
        cacheRelease(release, false);
        return toReleaseReference(release);
    }

    private QuantConfigReleaseReference toReleaseReference(QuantConfigReleaseVo release) {
        QuantConfigReleaseReference reference = new QuantConfigReleaseReference();
        reference.setReleaseVersion(release.getReleaseVersion());
        reference.setReleaseChecksum(release.getChecksum());
        Map<String, QuantConfigReleaseReference.GroupReference> groups = new HashMap<>();
        for (QuantConfigVersionVo item : release.getItems()) {
            QuantConfigReleaseReference.GroupReference group = new QuantConfigReleaseReference.GroupReference();
            group.setConfigVersion(item.getConfigVersion());
            group.setSchemaVersion(item.getSchemaVersion());
            group.setChecksum(item.getChecksum());
            groups.put(item.getConfigCode(), group);
        }
        reference.setGroups(groups);
        return reference;
    }

    private ReleasePlan preparePlan(List<Long> ids, boolean forUpdate) {
        if (ids == null || ids.size() != QuantConfigCodeEnum.values().length || new HashSet<>(ids).size() != ids.size()) {
            throw new ServiceException("发布清单必须包含每个配置分组的一个唯一版本");
        }
        List<QuantConfigVersion> items = versionMapper.selectByIdsForRelease(ids, forUpdate);
        if (items.size() != QuantConfigCodeEnum.values().length) {
            throw new ServiceException("发布清单包含不存在的配置版本");
        }
        Set<String> codes = new HashSet<>();
        for (QuantConfigVersion item : items) {
            if (!QuantConfigVersionStatusEnum.VALIDATED.getCode().equals(item.getStatus())) {
                throw new ServiceException("发布清单只能引用已校验配置版本");
            }
            if (!codes.add(item.getConfigCode())) {
                throw new ServiceException("发布清单不能包含重复配置分组");
            }
        }
        if (codes.size() != QuantConfigCodeEnum.values().length) {
            throw new ServiceException("发布清单缺少必需配置分组");
        }
        items.sort(Comparator.comparing(QuantConfigVersion::getConfigCode));
        List<String> checksumParts = new ArrayList<>();
        for (QuantConfigVersion item : items) {
            checksumParts.add(item.getConfigCode() + ":" + item.getConfigVersion() + ":" + item.getChecksum());
        }
        return new ReleasePlan(items, jsonSupport.checksum(String.join("\n", checksumParts)));
    }

    private QuantConfigReleaseVo persistRelease(ReleasePlan requestedPlan, QuantConfigReleaseBo bo, Long rollbackOf) {
        ReleasePlan lockedPlan = preparePlan(requestedPlan.items().stream().map(QuantConfigVersion::getId).toList(), true);
        if (!Objects.equals(requestedPlan.releaseChecksum(), lockedPlan.releaseChecksum())) {
            throw new ServiceException("QUANT_CONFIG_VERSION_MISMATCH");
        }
        QuantConfigRelease release = new QuantConfigRelease();
        release.setId(IdGeneratorUtil.nextLongId());
        release.setReleaseVersion(releaseMapper.selectNextReleaseVersion());
        release.setStatus("PUBLISHED");
        release.setChecksum(lockedPlan.releaseChecksum());
        release.setEffectiveFrom(bo.getEffectiveFrom());
        release.setPublishedBy(LoginHelper.getUserId());
        release.setPublishedAt(OffsetDateTime.now());
        release.setRollbackOfReleaseVersion(rollbackOf);
        release.setChangeSummary(bo.getChangeSummary());
        if (releaseMapper.insert(release) != 1) {
            throw new ServiceException("创建量化配置发布版本失败");
        }
        for (QuantConfigVersion item : lockedPlan.items()) {
            QuantConfigReleaseItem releaseItem = new QuantConfigReleaseItem();
            releaseItem.setId(IdGeneratorUtil.nextLongId());
            releaseItem.setReleaseId(release.getId());
            releaseItem.setConfigCode(item.getConfigCode());
            releaseItem.setConfigVersionId(item.getId());
            releaseItem.setConfigVersion(item.getConfigVersion());
            releaseItem.setConfigChecksum(item.getChecksum());
            releaseItem.setSchemaVersion(item.getSchemaVersion());
            if (releaseMapper.insertItem(releaseItem) != 1) {
                throw new ServiceException("保存量化配置发布条目失败");
            }
        }
        return toReleaseVo(release, lockedPlan.items());
    }

    private QuantConfigProviderValidationRequest toProviderRequest(ReleasePlan plan) {
        List<QuantConfigProviderValidationRequest.ConfigItem> configs = new ArrayList<>();
        for (QuantConfigVersion item : plan.items()) {
            QuantConfigProviderValidationRequest.ConfigItem config = new QuantConfigProviderValidationRequest.ConfigItem();
            config.setConfigCode(item.getConfigCode());
            config.setConfigVersion(item.getConfigVersion());
            config.setSchemaVersion(item.getSchemaVersion());
            config.setConfigJson(item.getConfigJson());
            config.setChecksum(item.getChecksum());
            configs.add(config);
        }
        QuantConfigProviderValidationRequest request = new QuantConfigProviderValidationRequest();
        request.setConfigs(configs);
        request.setReleaseChecksum(plan.releaseChecksum());
        return request;
    }

    private QuantConfigReleaseVo toReleaseVo(QuantConfigRelease release, List<QuantConfigVersion> items) {
        QuantConfigReleaseVo result = new QuantConfigReleaseVo();
        result.setId(release.getId());
        result.setReleaseVersion(release.getReleaseVersion());
        result.setStatus(release.getStatus());
        result.setChecksum(release.getChecksum());
        result.setEffectiveFrom(release.getEffectiveFrom());
        result.setPublishedAt(release.getPublishedAt());
        result.setRollbackOfReleaseVersion(release.getRollbackOfReleaseVersion());
        result.setChangeSummary(release.getChangeSummary());
        List<QuantConfigVersionVo> views = new ArrayList<>();
        for (QuantConfigVersion item : items) {
            QuantConfigVersionVo view = new QuantConfigVersionVo();
            view.setId(item.getId());
            view.setConfigCode(item.getConfigCode());
            view.setConfigVersion(item.getConfigVersion());
            view.setSchemaVersion(item.getSchemaVersion());
            view.setStatus(item.getStatus());
            view.setConfigJson(item.getConfigJson());
            view.setCanonicalJson(jsonSupport.canonicalize(jsonSupport.readObject(item.getConfigJson())));
            view.setChecksum(item.getChecksum());
            views.add(view);
        }
        result.setItems(views);
        return result;
    }

    private void projectRelease(QuantConfigReleaseVo release) {
        QuantConfigRelease active = releaseMapper.selectActiveRelease();
        cacheRelease(release, active != null && Objects.equals(active.getReleaseVersion(), release.getReleaseVersion()));
        RedisUtils.publish(QuantConfigCacheConstants.INVALIDATE_CHANNEL, release.getReleaseVersion());
    }

    /** PostgreSQL 校验过的发布记录可随时重建 Redis 投影，不改变任何历史发布。 */
    private void cacheRelease(QuantConfigReleaseVo release, boolean active) {
        RedisUtils.setCacheObject(QuantConfigCacheConstants.RELEASE_KEY_PREFIX + release.getReleaseVersion(), release);
        if (active) {
            RedisUtils.setCacheObject(QuantConfigCacheConstants.ACTIVE_RELEASE_KEY, release);
        }
        for (QuantConfigVersionVo item : release.getItems()) {
            RedisUtils.setCacheObject(
                QuantConfigCacheConstants.GROUP_KEY_PREFIX + item.getConfigCode() + ":" + item.getConfigVersion(),
                item,
                properties.getGroupCacheTtl()
            );
        }
    }

    private Map<String, String> flattenJson(JsonNode root) {
        Map<String, String> fields = new HashMap<>();
        flattenJson(root, "", fields);
        return fields;
    }

    private void flattenJson(JsonNode node, String path, Map<String, String> fields) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> flattenJson(
                entry.getValue(), path.isEmpty() ? entry.getKey() : path + "." + entry.getKey(), fields));
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                flattenJson(node.get(index), path + "[" + index + "]", fields);
            }
            return;
        }
        fields.put(path, jsonSupport.canonicalize(node));
    }

    private record ReleasePlan(List<QuantConfigVersion> items, String releaseChecksum) {
    }
}
