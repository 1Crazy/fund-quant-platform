package org.dromara.fund.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.fund.client.QuantConfigProviderClient;
import org.dromara.fund.config.QuantConfigProperties;
import org.dromara.fund.domain.QuantConfigRelease;
import org.dromara.fund.domain.QuantConfigReleaseItem;
import org.dromara.fund.domain.QuantConfigVersion;
import org.dromara.fund.domain.bo.QuantConfigDraftBo;
import org.dromara.fund.domain.bo.QuantConfigReleaseBo;
import org.dromara.fund.domain.dto.QuantConfigReleaseReference;
import org.dromara.fund.domain.enums.QuantConfigCodeEnum;
import org.dromara.fund.domain.vo.QuantConfigReleaseVo;
import org.dromara.fund.domain.vo.QuantConfigVersionVo;
import org.dromara.fund.mapper.QuantConfigReleaseMapper;
import org.dromara.fund.mapper.QuantConfigVersionMapper;
import org.dromara.fund.service.QuantConfigJsonSupport;
import org.dromara.fund.service.QuantConfigValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.transaction.TransactionStatus;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.redisson.api.RedissonClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 关键发布路径的服务级契约：失败不投影，成功才形成完整、可审计的发布清单。 */
@Tag("dev")
final class QuantConfigServiceImplTest {

    @BeforeAll
    static void installStaticUtilityBeans() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        IdentifierGenerator identifierGenerator = mock(IdentifierGenerator.class);
        when(identifierGenerator.nextId(any())).thenReturn(1L);
        when(applicationContext.getBean(IdentifierGenerator.class)).thenReturn(identifierGenerator);
        when(applicationContext.getBean(RedissonClient.class)).thenReturn(mock(RedissonClient.class));
        new SpringUtils().setApplicationContext(applicationContext);
    }

    private final QuantConfigVersionMapper versionMapper = mock(QuantConfigVersionMapper.class);
    private final QuantConfigReleaseMapper releaseMapper = mock(QuantConfigReleaseMapper.class);
    private final QuantConfigProviderClient providerClient = mock(QuantConfigProviderClient.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final QuantConfigServiceImpl service = new QuantConfigServiceImpl(
        versionMapper,
        releaseMapper,
        new QuantConfigJsonSupport(new ObjectMapper()),
        new QuantConfigValidator(),
        providerClient,
        new QuantConfigProperties(),
        transactionTemplate
    );

    @Test
    void publishedConfigVersionMustNotBeEditable() {
        QuantConfigVersion published = version(1L, QuantConfigCodeEnum.ESTIMATE, "PUBLISHED");
        when(versionMapper.selectById(1L)).thenReturn(published);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.updateDraft(1L, draft(published)));

        assertEquals("仅草稿版本允许编辑", exception.getMessage());
        verify(versionMapper, never()).updateDraft(any(), any());
    }

    @Test
    void staleDraftRevisionMustBeRejectedWithoutReadingOrWritingAnotherVersion() {
        QuantConfigVersion existing = version(1L, QuantConfigCodeEnum.ESTIMATE, "DRAFT");
        when(versionMapper.selectById(1L)).thenReturn(existing);
        when(versionMapper.updateDraft(any(QuantConfigVersion.class), eq(0L))).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.updateDraft(1L, draft(existing)));

        assertTrue(exception.getMessage().contains("其他操作更新"));
        verify(versionMapper, never()).selectVersionVo(any());
    }

    @Test
    void incompleteReleaseMustFailBeforeProviderValidationOrTransaction() {
        when(releaseMapper.selectCount(isNull())).thenReturn(1L);
        QuantConfigReleaseBo release = releaseBo(validatedVersions().subList(0, QuantConfigCodeEnum.values().length - 1));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.publish(release));

        assertTrue(exception.getMessage().contains("发布清单必须包含"));
        verify(providerClient, never()).verify(any());
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    void completeReleaseMustPersistEveryItemWithAuditMetadataAfterProviderValidation() {
        List<QuantConfigVersion> versions = validatedVersions();
        configurePublish(versions, true);

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            QuantConfigReleaseVo published = service.publish(releaseBo(versions));

            assertEquals(42L, published.getReleaseVersion());
            assertEquals(QuantConfigCodeEnum.values().length, published.getItems().size());
            ArgumentCaptor<QuantConfigRelease> releaseCaptor = ArgumentCaptor.forClass(QuantConfigRelease.class);
            ArgumentCaptor<QuantConfigReleaseItem> itemCaptor = ArgumentCaptor.forClass(QuantConfigReleaseItem.class);
            verify(releaseMapper).insert(releaseCaptor.capture());
            verify(releaseMapper, times(QuantConfigCodeEnum.values().length)).insertItem(itemCaptor.capture());

            QuantConfigRelease persisted = releaseCaptor.getValue();
            assertEquals("PUBLISHED", persisted.getStatus());
            assertEquals("D-011 首发", persisted.getChangeSummary());
            assertNotNull(persisted.getPublishedAt());
            assertEquals(
                versions.stream().map(QuantConfigVersion::getChecksum).sorted().toList(),
                itemCaptor.getAllValues().stream().map(QuantConfigReleaseItem::getConfigChecksum).sorted().toList()
            );

            InOrder order = Mockito.inOrder(providerClient, transactionTemplate);
            order.verify(providerClient).verify(any());
            order.verify(transactionTemplate).execute(any());
        }
    }

    @Test
    void failedReleaseItemInsertMustNotProjectAPartialReleaseToRedis() {
        List<QuantConfigVersion> versions = validatedVersions();
        configurePublish(versions, false);

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            ServiceException exception = assertThrows(ServiceException.class, () -> service.publish(releaseBo(versions)));

            assertEquals("保存量化配置发布条目失败", exception.getMessage());
            redis.verifyNoInteractions();
        }
    }

    @Test
    void rollbackMustCreateANewReleaseThatRetainsTheSourceReleaseLineage() {
        List<QuantConfigVersion> versions = validatedVersions();
        configurePublish(versions, true);
        QuantConfigReleaseVo source = releaseVo(3L, "c".repeat(64), versions);
        when(releaseMapper.selectReleaseVo(3L)).thenReturn(source);
        when(releaseMapper.selectReleaseItems(3L)).thenReturn(source.getItems());

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            service.rollback(3L, releaseBo(versions));

            ArgumentCaptor<QuantConfigRelease> releaseCaptor = ArgumentCaptor.forClass(QuantConfigRelease.class);
            ArgumentCaptor<QuantConfigReleaseItem> itemCaptor = ArgumentCaptor.forClass(QuantConfigReleaseItem.class);
            verify(releaseMapper).insert(releaseCaptor.capture());
            verify(releaseMapper, times(QuantConfigCodeEnum.values().length)).insertItem(itemCaptor.capture());
            assertEquals(3L, releaseCaptor.getValue().getRollbackOfReleaseVersion());
            assertEquals(42L, releaseCaptor.getValue().getReleaseVersion());
            assertEquals("PUBLISHED", releaseCaptor.getValue().getStatus());
            assertEquals(
                source.getItems().stream().map(QuantConfigVersionVo::getConfigVersion).sorted().toList(),
                itemCaptor.getAllValues().stream().map(QuantConfigReleaseItem::getConfigVersion).sorted().toList()
            );
            assertEquals(3L, source.getReleaseVersion());
            assertEquals("PUBLISHED", source.getStatus());
            verify(providerClient).verify(any());
        }
    }

    @Test
    void resolvingTheActiveReleaseMustFailClosedWhenNoReleaseIsEffective() {
        when(releaseMapper.selectActiveRelease()).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class, service::resolveActiveRelease);

        assertEquals("QUANT_CONFIG_NOT_PUBLISHED", exception.getMessage());
        verify(releaseMapper, never()).selectReleaseVo(any());
    }

    @Test
    void resolvingTheActiveReleaseMustReturnTheExactReleaseAndGroupLineage() {
        List<QuantConfigVersion> versions = validatedVersions();
        QuantConfigRelease active = new QuantConfigRelease();
        active.setReleaseVersion(9L);
        QuantConfigReleaseVo published = releaseVo(9L, "d".repeat(64), versions);
        when(releaseMapper.selectActiveRelease()).thenReturn(active);
        when(releaseMapper.selectReleaseVo(9L)).thenReturn(published);
        when(releaseMapper.selectReleaseItems(9L)).thenReturn(published.getItems());

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            QuantConfigReleaseReference reference = service.resolveActiveRelease();

            assertEquals(9L, reference.getReleaseVersion());
            assertEquals("d".repeat(64), reference.getReleaseChecksum());
            assertEquals(QuantConfigCodeEnum.values().length, reference.getGroups().size());
            QuantConfigReleaseReference.GroupReference estimate = reference.getGroups().get("ESTIMATE");
            assertNotNull(estimate);
            QuantConfigVersion estimateVersion = versions.stream()
                .filter(item -> "ESTIMATE".equals(item.getConfigCode()))
                .findFirst()
                .orElseThrow();
            assertEquals(estimateVersion.getConfigVersion(), estimate.getConfigVersion());
            assertEquals(estimateVersion.getChecksum(), estimate.getChecksum());
        }
    }

    @Test
    void resolvingHistoricalReleaseMustNotFallBackToTheActiveRelease() {
        List<QuantConfigVersion> versions = validatedVersions();
        QuantConfigReleaseVo historical = releaseVo(3L, "e".repeat(64), versions);
        when(releaseMapper.selectReleaseVo(3L)).thenReturn(historical);
        when(releaseMapper.selectReleaseItems(3L)).thenReturn(historical.getItems());

        try (MockedStatic<RedisUtils> redis = Mockito.mockStatic(RedisUtils.class)) {
            QuantConfigReleaseReference reference = service.resolveRelease(3L);

            assertEquals(3L, reference.getReleaseVersion());
            assertEquals("e".repeat(64), reference.getReleaseChecksum());
            verify(releaseMapper, never()).selectActiveRelease();
        }
    }

    private void configurePublish(List<QuantConfigVersion> versions, boolean insertItems) {
        when(releaseMapper.selectCount(isNull())).thenReturn(1L);
        when(versionMapper.selectByIdsForRelease(anyList(), eq(false)))
            .thenAnswer(invocation -> new ArrayList<>(versions));
        when(versionMapper.selectByIdsForRelease(anyList(), eq(true)))
            .thenAnswer(invocation -> new ArrayList<>(versions));
        when(releaseMapper.selectNextReleaseVersion()).thenReturn(42L);
        when(releaseMapper.insert(any(QuantConfigRelease.class))).thenReturn(1);
        when(releaseMapper.insertItem(any(QuantConfigReleaseItem.class))).thenReturn(insertItems ? 1 : 0);
        when(releaseMapper.selectActiveRelease()).thenReturn(null);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<QuantConfigReleaseVo> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private QuantConfigReleaseBo releaseBo(List<QuantConfigVersion> versions) {
        QuantConfigReleaseBo bo = new QuantConfigReleaseBo();
        bo.setConfigVersionIds(versions.stream().map(QuantConfigVersion::getId).toList());
        bo.setEffectiveFrom(OffsetDateTime.now().plusMinutes(1));
        bo.setChangeSummary("D-011 首发");
        return bo;
    }

    private QuantConfigDraftBo draft(QuantConfigVersion item) {
        QuantConfigDraftBo bo = new QuantConfigDraftBo();
        bo.setConfigCode(item.getConfigCode());
        bo.setSchemaVersion(item.getSchemaVersion());
        bo.setConfigJson(item.getConfigJson());
        bo.setRevision(item.getRevision());
        return bo;
    }

    private QuantConfigReleaseVo releaseVo(Long releaseVersion, String checksum, List<QuantConfigVersion> versions) {
        QuantConfigReleaseVo release = new QuantConfigReleaseVo();
        release.setReleaseVersion(releaseVersion);
        release.setStatus("PUBLISHED");
        release.setChecksum(checksum);
        release.setItems(versions.stream().map(this::versionVo).toList());
        return release;
    }

    private QuantConfigVersionVo versionVo(QuantConfigVersion version) {
        QuantConfigVersionVo view = new QuantConfigVersionVo();
        view.setId(version.getId());
        view.setConfigCode(version.getConfigCode());
        view.setConfigVersion(version.getConfigVersion());
        view.setSchemaVersion(version.getSchemaVersion());
        view.setChecksum(version.getChecksum());
        return view;
    }

    private List<QuantConfigVersion> validatedVersions() {
        List<QuantConfigVersion> versions = new ArrayList<>();
        long id = 1L;
        for (QuantConfigCodeEnum code : QuantConfigCodeEnum.values()) {
            versions.add(version(id++, code, "VALIDATED"));
        }
        return versions;
    }

    private QuantConfigVersion version(Long id, QuantConfigCodeEnum code, String status) {
        QuantConfigVersion item = new QuantConfigVersion();
        item.setId(id);
        item.setConfigCode(code.getCode());
        item.setConfigVersion(id.intValue());
        item.setSchemaVersion(1);
        item.setStatus(status);
        item.setConfigJson("{}");
        item.setChecksum("a".repeat(63) + Integer.toHexString(id.intValue()));
        item.setRevision(0L);
        return item;
    }
}
