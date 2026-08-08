<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';
import { Check, Plus, RotateCw, Search, SquareCode, Undo2 } from '@vben/icons';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElCheckbox,
  ElCheckboxGroup,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElEmpty,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElPopconfirm,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTabs,
  ElTabPane,
} from 'element-plus';
import { storeToRefs } from 'pinia';

import type { FundApi } from '#/api/fund';
import { useFundStore } from '#/store';

import {
  quantConfigEditPermissions,
  quantConfigGroupLabel,
  quantConfigPublishPermissions,
  quantConfigRollbackPermissions,
  quantConfigStatusMeta,
  quantConfigValidatePermissions,
} from '../utils/status';

type FieldType = 'boolean' | 'json' | 'null' | 'number' | 'string';

interface DraftFieldRow {
  key: string;
  type: FieldType;
  value: string;
}

const fundStore = useFundStore();
const { hasAccessByCodes } = useAccess();
const {
  configDiff,
  configGroups,
  configGroupsLoading,
  configMutationLoading,
  configReleaseQuery,
  configReleases,
  configReleasesLoading,
  configReleasesTotal,
  configValidation,
  configVersionQuery,
  configVersions,
  configVersionsLoading,
  configVersionsTotal,
  selectedConfigVersion,
} = storeToRefs(fundStore);

const activeTab = ref('drafts');
const selectedGroupCode = ref<FundApi.QuantConfigCode>('');
const selectedVersionIds = ref<Array<number | string>>([]);
const diffDrawerVisible = ref(false);
const jsonError = ref('');
const fieldRows = ref<DraftFieldRow[]>([]);

const draftForm = reactive<FundApi.QuantConfigDraftPayload>({
  configCode: '',
  configJson: {},
  effectiveFrom: '',
  remark: '',
  revision: 0,
  schemaVersion: 1,
});

const publishForm = reactive({
  effectiveFrom: '',
  remark: '',
});

const canEdit = computed(() => hasAccessByCodes(quantConfigEditPermissions));
const canPublish = computed(() =>
  hasAccessByCodes(quantConfigPublishPermissions),
);
const canRollback = computed(() =>
  hasAccessByCodes(quantConfigRollbackPermissions),
);
const canValidate = computed(() =>
  hasAccessByCodes(quantConfigValidatePermissions),
);
const canMutateDraft = computed(
  () => canEdit.value && (!draftForm.id || selectedConfigVersion.value?.status === 'DRAFT'),
);
const selectedGroup = computed(() =>
  configGroups.value.find((group) => group.configCode === selectedGroupCode.value),
);
const selectedVersionLabel = computed(() => {
  const version = selectedConfigVersion.value;
  if (!version) return '尚未选择版本';
  return `${version.configCode} v${version.configVersion ?? 'draft'}`;
});
const allValidationIssues = computed(() => configValidation.value?.issues ?? []);
const readonlyJsonPreview = computed(() => {
  return (
    configValidation.value?.canonicalJson ||
    selectedConfigVersion.value?.normalizedJson ||
    stringifyJson(buildConfigJson(false))
  );
});
const publishSelectionLabel = computed(() => {
  if (!selectedVersionIds.value.length) return '未选择配置组版本';
  return `已选择 ${selectedVersionIds.value.length} 个已校验配置组版本`;
});

function stringifyJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2);
}

function inferFieldType(value: FundApi.JsonValue): FieldType {
  if (value === null) return 'null';
  if (Array.isArray(value) || typeof value === 'object') return 'json';
  if (typeof value === 'boolean') return 'boolean';
  if (typeof value === 'number') return 'number';
  return 'string';
}

function toFieldRows(value: FundApi.JsonValue): DraftFieldRow[] {
  if (!value || Array.isArray(value) || typeof value !== 'object') return [];
  return Object.entries(value).map(([key, entry]) => ({
    key,
    type: inferFieldType(entry),
    value:
      entry === null || typeof entry === 'object'
        ? stringifyJson(entry)
        : String(entry),
  }));
}

function parseFieldValue(row: DraftFieldRow) {
  if (row.type === 'null') return null;
  if (row.type === 'boolean') return row.value === 'true';
  if (row.type === 'number') {
    const numericValue = Number(row.value);
    if (!Number.isFinite(numericValue)) {
      throw new Error(`${row.key} 不是有效数字`);
    }
    return numericValue;
  }
  if (row.type === 'json') {
    return JSON.parse(row.value || 'null') as FundApi.JsonValue;
  }
  return row.value;
}

function buildConfigJson(throwOnError = true) {
  try {
    const result: Record<string, FundApi.JsonValue> = {};
    for (const row of fieldRows.value) {
      const key = row.key.trim();
      if (!key) continue;
      result[key] = parseFieldValue({ ...row, key }) as FundApi.JsonValue;
    }
    if (throwOnError) jsonError.value = '';
    return result;
  } catch (error) {
    const message = error instanceof Error ? error.message : 'JSON 格式无效';
    if (throwOnError) {
      jsonError.value = message;
      throw error;
    }
    return {};
  }
}

function errorMessage(error: unknown) {
  if (error instanceof Error) return error.message;
  return '服务端拒绝校验请求';
}

function loadVersionIntoDraft(version: FundApi.QuantConfigVersion) {
  Object.assign(draftForm, {
    configCode: version.configCode,
    configJson: version.configJson,
    effectiveFrom: version.effectiveFrom ?? '',
    id: version.id,
    remark: version.remark ?? '',
    revision: version.revision ?? 0,
    schemaVersion: version.schemaVersion,
  });
  selectedGroupCode.value = version.configCode;
  fieldRows.value = toFieldRows(version.configJson);
  selectedVersionIds.value = version.status === 'VALIDATED' ? [version.id] : [];
  jsonError.value = '';
}

function startNewDraft(groupCode = selectedGroupCode.value) {
  Object.assign(draftForm, {
    configCode: groupCode,
    configJson: {},
    effectiveFrom: '',
    id: undefined,
    remark: '',
    revision: 0,
    schemaVersion: selectedGroup.value?.schemaVersion ?? 1,
  });
  fieldRows.value = [];
  selectedConfigVersion.value = undefined;
  configValidation.value = undefined;
  jsonError.value = '';
}

function addFieldRow() {
  fieldRows.value.push({ key: '', type: 'string', value: '' });
}

function removeFieldRow(index: number) {
  fieldRows.value.splice(index, 1);
}

async function selectGroup(group: FundApi.QuantConfigGroup) {
  selectedGroupCode.value = group.configCode;
  configVersionQuery.value.configCode = group.configCode;
  startNewDraft(group.configCode);
  await fundStore.fetchConfigVersions(true);
}

async function searchVersions() {
  await fundStore.fetchConfigVersions(true);
}

async function resetVersionQuery() {
  fundStore.resetConfigVersionQuery();
  selectedGroupCode.value = '';
  await fundStore.fetchConfigVersions(true);
}

function changeVersionPage() {
  void fundStore.fetchConfigVersions();
}

function changeVersionPageSize() {
  void fundStore.fetchConfigVersions(true);
}

async function selectVersion(row: FundApi.QuantConfigVersion) {
  const version = await fundStore.fetchConfigVersion(row.id);
  loadVersionIntoDraft(version);
}

async function saveDraft() {
  if (!draftForm.configCode) {
    ElMessage.warning('请选择配置分组');
    return;
  }
  const result = await fundStore.saveConfigDraft({
    ...draftForm,
    configJson: buildConfigJson(),
  });
  loadVersionIntoDraft(result);
  ElMessage.success('草稿已保存');
}

async function validateDraft() {
  if (!draftForm.configCode) {
    ElMessage.warning('请选择配置分组');
    return;
  }
  if (!draftForm.id) {
    const result = await fundStore.saveConfigDraft({
      ...draftForm,
      configJson: buildConfigJson(),
    });
    loadVersionIntoDraft(result);
  }
  if (!draftForm.id) return;
  try {
    const result = await fundStore.validateConfigDraft(draftForm.id);
    if (result.passed) {
      ElMessage.success('校验通过');
      return;
    }
    ElMessage.warning('校验未通过，请查看反馈');
  } catch (error) {
    configValidation.value = {
      issues: [
        {
          code: 'QUANT_CONFIG_VALIDATION_FAILED',
          level: 'ERROR',
          message: errorMessage(error),
        },
      ],
      passed: false,
    };
  }
}

async function openDiff(row: FundApi.QuantConfigVersion) {
  await fundStore.fetchConfigDiff({
    baseId:
      selectedConfigVersion.value?.id === row.id
        ? undefined
        : selectedConfigVersion.value?.id,
    targetId: row.id,
  });
  diffDrawerVisible.value = true;
}

async function publishRelease() {
  if (!selectedVersionIds.value.length) {
    ElMessage.warning('请选择要发布的已校验配置组版本');
    return;
  }
  if (!publishForm.effectiveFrom) {
    ElMessage.warning('请选择发布生效时间');
    return;
  }
  const release = await fundStore.publishConfigRelease({
    changeSummary: publishForm.remark || undefined,
    configVersionIds: selectedVersionIds.value,
    effectiveFrom: publishForm.effectiveFrom || undefined,
  });
  ElMessage.success(`发布版本 v${release.releaseVersion} 已创建`);
}

async function rollbackRelease(row: FundApi.QuantConfigRelease) {
  const configVersionIds = row.items
    .map((item) => item.id)
    .filter((id): id is number | string => id != null);
  if (!configVersionIds.length) {
    ElMessage.warning('发布历史缺少配置版本 ID，无法回滚');
    return;
  }
  const release = await fundStore.rollbackConfigRelease({
    changeSummary: `回滚到发布版本 v${row.releaseVersion}`,
    configVersionIds,
    effectiveFrom: new Date(Date.now() + 60_000).toISOString(),
    sourceReleaseVersion: row.releaseVersion,
  });
  ElMessage.success(`回滚发布 v${release.releaseVersion} 已创建`);
}

async function searchReleases() {
  await fundStore.fetchConfigReleases(true);
}

function changeReleasePage() {
  void fundStore.fetchConfigReleases();
}

function changeReleasePageSize() {
  void fundStore.fetchConfigReleases(true);
}

onMounted(async () => {
  await fundStore.fetchConfigGroups();
  if (configGroups.value[0]) {
    await selectGroup(configGroups.value[0]);
  } else {
    await fundStore.fetchConfigVersions();
  }
  await fundStore.fetchConfigReleases();
});
</script>

<template>
  <Page auto-content-height>
    <div class="fund-config-page flex h-full min-h-0 flex-col gap-4">
      <section class="config-header">
        <div>
          <h1>量化配置中心</h1>
        </div>
        <div class="config-health">
          <ElTag effect="plain" type="primary">{{ selectedVersionLabel }}</ElTag>
        </div>
      </section>

      <ElAlert
        show-icon
        title="首个发布版本仍受 D-011 用户确认门禁约束；本页面不会填入或推断初始数学数值。"
        type="warning"
        :closable="false"
      />

      <div class="grid min-h-0 flex-1 gap-4 xl:grid-cols-[300px_minmax(0,1fr)]">
        <ElCard class="min-h-0" shadow="never">
          <template #header>
            <div class="flex items-center justify-between gap-3">
              <span class="panel-title">配置分组</span>
              <ElButton
                :loading="configGroupsLoading"
                link
                type="primary"
                @click="fundStore.fetchConfigGroups"
              >
                <RotateCw class="mr-1 size-4" />刷新
              </ElButton>
            </div>
          </template>
          <div v-if="configGroups.length" class="group-list">
            <button
              v-for="group in configGroups"
              :key="group.configCode"
              class="group-item"
              :class="{ active: selectedGroupCode === group.configCode }"
              type="button"
              @click="selectGroup(group)"
            >
              <span>{{ group.displayName || quantConfigGroupLabel(group.configCode) }}</span>
              <ElTag :type="quantConfigStatusMeta(group.status).type" effect="plain" size="small">
                {{ quantConfigStatusMeta(group.status).label }}
              </ElTag>
              <small>
                当前 v{{ group.activeConfigVersion ?? '--' }} · 最新 v{{ group.latestConfigVersion ?? '--' }}
              </small>
            </button>
          </div>
          <ElEmpty v-else description="暂无配置分组" />
        </ElCard>

        <ElTabs v-model="activeTab" class="min-h-0">
          <ElTabPane label="草稿与校验" name="drafts">
            <div class="grid gap-4 2xl:grid-cols-[minmax(0,1fr)_420px]">
              <ElCard shadow="never">
                <template #header>
                  <div class="flex flex-wrap items-center justify-between gap-3">
                    <span class="panel-title">分组结构化草稿</span>
                    <div class="flex gap-2">
                      <ElButton :disabled="!canEdit" @click="startNewDraft()">
                        <Plus class="mr-1 size-4" />新草稿
                      </ElButton>
                      <ElButton
                        :disabled="!canMutateDraft"
                        :loading="configMutationLoading"
                        type="primary"
                        @click="saveDraft"
                      >
                        保存草稿
                      </ElButton>
                      <ElButton
                        :disabled="!canValidate"
                        :loading="configMutationLoading"
                        type="success"
                        @click="validateDraft"
                      >
                        <Check class="mr-1 size-4" />服务端校验
                      </ElButton>
                    </div>
                  </div>
                </template>

                <div class="grid gap-3 md:grid-cols-[180px_120px_1fr]">
                  <ElSelect v-model="draftForm.configCode" placeholder="配置分组">
                    <ElOption
                      v-for="group in configGroups"
                      :key="group.configCode"
                      :label="group.displayName || quantConfigGroupLabel(group.configCode)"
                      :value="group.configCode"
                    />
                  </ElSelect>
                  <ElInput v-model.number="draftForm.schemaVersion" placeholder="结构版本" />
                  <ElDatePicker
                    v-model="draftForm.effectiveFrom"
                    class="w-full"
                    placeholder="生效时间"
                    type="datetime"
                    value-format="YYYY-MM-DD HH:mm:ss"
                  />
                </div>
                <ElInput
                  v-model="draftForm.remark"
                  class="mt-3"
                  maxlength="200"
                  placeholder="变更说明"
                  show-word-limit
                />

                <div class="field-editor mt-4">
                  <div class="field-editor-head">
                    <span>字段</span>
                    <span>类型</span>
                    <span>值</span>
                    <span></span>
                  </div>
                  <div v-for="(row, index) in fieldRows" :key="index" class="field-row">
                    <ElInput v-model="row.key" placeholder="字段路径或名称" />
                    <ElSelect v-model="row.type" placeholder="类型">
                      <ElOption label="文本" value="string" />
                      <ElOption label="数字" value="number" />
                      <ElOption label="布尔" value="boolean" />
                      <ElOption label="JSON" value="json" />
                      <ElOption label="Null" value="null" />
                    </ElSelect>
                    <ElInput
                      v-model="row.value"
                      :autosize="{ minRows: row.type === 'json' ? 2 : 1, maxRows: 8 }"
                      placeholder="字段值"
                      type="textarea"
                    />
                    <ElButton link type="danger" @click="removeFieldRow(index)">移除</ElButton>
                  </div>
                  <ElButton class="mt-3" :disabled="!canEdit" @click="addFieldRow">
                    <Plus class="mr-1 size-4" />添加字段
                  </ElButton>
                </div>
                <ElAlert
                  v-if="jsonError"
                  class="mt-3"
                  :closable="false"
                  :title="jsonError"
                  type="error"
                />
              </ElCard>

              <div class="flex min-h-0 flex-col gap-4">
                <ElCard shadow="never">
                  <template #header><span class="panel-title">校验反馈</span></template>
                  <ElDescriptions :column="1" size="small">
                    <ElDescriptionsItem label="校验状态">
                      <ElTag
                        :type="configValidation?.passed ? 'success' : 'info'"
                        effect="plain"
                      >
                        {{ configValidation ? (configValidation.passed ? '通过' : '未通过') : '未校验' }}
                      </ElTag>
                    </ElDescriptionsItem>
                    <ElDescriptionsItem label="配置校验和">
                      <span class="checksum">{{ configValidation?.checksum || selectedConfigVersion?.checksum || '--' }}</span>
                    </ElDescriptionsItem>
                  </ElDescriptions>
                  <div v-if="allValidationIssues.length" class="mt-3 flex flex-col gap-2">
                    <ElAlert
                      v-for="issue in allValidationIssues"
                      :key="`${issue.code}-${issue.fieldPath}-${issue.message}`"
                      :closable="false"
                      :title="`${issue.code} · ${issue.fieldPath || 'GLOBAL'}`"
                      :type="issue.level === 'ERROR' ? 'error' : 'warning'"
                    >
                      {{ issue.message }}
                    </ElAlert>
                  </div>
                  <ElEmpty v-else class="compact-empty" description="暂无校验反馈" />
                </ElCard>

                <ElCard class="min-h-0 flex-1" shadow="never">
                  <template #header>
                    <div class="flex items-center gap-2">
                      <SquareCode class="size-4" />
                      <span class="panel-title">只读规范化 JSON 预览</span>
                    </div>
                  </template>
                  <pre class="json-preview">{{ readonlyJsonPreview }}</pre>
                </ElCard>
              </div>
            </div>
          </ElTabPane>

          <ElTabPane label="版本列表与 Diff" name="versions">
            <ElCard class="min-h-0" shadow="never">
              <template #header>
                <div class="flex flex-wrap items-center justify-between gap-3">
                  <span class="panel-title">配置版本</span>
                  <div class="grid gap-2 lg:grid-cols-[170px_140px_auto]">
                    <ElSelect
                      v-model="configVersionQuery.status"
                      clearable
                      placeholder="状态"
                    >
                      <ElOption label="草稿" value="DRAFT" />
                      <ElOption label="已校验" value="VALIDATED" />
                      <ElOption label="已发布" value="PUBLISHED" />
                      <ElOption label="已停用" value="RETIRED" />
                    </ElSelect>
                    <ElInput
                      v-model="configVersionQuery.configCode"
                      clearable
                      placeholder="配置代码"
                      @keyup.enter="searchVersions"
                    />
                    <div class="flex gap-2">
                      <ElButton
                        :loading="configVersionsLoading"
                        type="primary"
                        @click="searchVersions"
                      >
                        <Search class="mr-1 size-4" />查询
                      </ElButton>
                      <ElButton @click="resetVersionQuery">
                        <RotateCw class="mr-1 size-4" />重置
                      </ElButton>
                    </div>
                  </div>
                </div>
              </template>

              <ElCheckboxGroup v-model="selectedVersionIds">
                <ElTable
                  v-loading="configVersionsLoading"
                  :data="configVersions"
                  height="460"
                  row-key="id"
                  stripe
                  @row-click="selectVersion"
                >
                  <ElTableColumn fixed label="发布选择" width="94">
                    <template #default="{ row }">
                      <ElCheckbox
                        :disabled="row.status !== 'VALIDATED'"
                        :label="row.id"
                        @click.stop
                      >
                        &nbsp;
                      </ElCheckbox>
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="配置组" min-width="160">
                    <template #default="{ row }">
                      {{ quantConfigGroupLabel(row.configCode) }}
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="配置版本" min-width="110">
                    <template #default="{ row }">v{{ row.configVersion ?? '--' }}</template>
                  </ElTableColumn>
                  <ElTableColumn label="结构版本" min-width="100" prop="schemaVersion" />
                  <ElTableColumn label="状态" min-width="110">
                    <template #default="{ row }">
                      <ElTag :type="quantConfigStatusMeta(row.status).type" effect="plain">
                        {{ quantConfigStatusMeta(row.status).label }}
                      </ElTag>
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="校验和" min-width="210" prop="checksum" show-overflow-tooltip />
                  <ElTableColumn label="生效时间" min-width="170" prop="effectiveFrom" />
                  <ElTableColumn label="更新人" min-width="110" prop="updatedBy" />
                  <ElTableColumn fixed="right" label="操作" min-width="128">
                    <template #default="{ row }">
                      <ElButton link type="primary" @click.stop="selectVersion(row)">
                        编辑
                      </ElButton>
                      <ElButton link type="primary" @click.stop="openDiff(row)">
                        Diff
                      </ElButton>
                    </template>
                  </ElTableColumn>
                  <template #empty>
                    <ElEmpty description="暂无配置版本" />
                  </template>
                </ElTable>
              </ElCheckboxGroup>
              <div class="mt-4 flex items-center justify-between gap-3">
                <span class="text-sm text-slate-500">{{ publishSelectionLabel }}</span>
                <ElPagination
                  v-model:current-page="configVersionQuery.pageNum"
                  v-model:page-size="configVersionQuery.pageSize"
                  :page-sizes="[10, 20, 50]"
                  :total="configVersionsTotal"
                  background
                  layout="total, sizes, prev, pager, next"
                  @current-change="changeVersionPage"
                  @size-change="changeVersionPageSize"
                />
              </div>
            </ElCard>
          </ElTabPane>

          <ElTabPane label="发布历史" name="releases">
            <div class="grid gap-4 xl:grid-cols-[360px_minmax(0,1fr)]">
              <ElCard shadow="never">
                <template #header><span class="panel-title">发布组合</span></template>
                <p class="release-note">{{ publishSelectionLabel }}</p>
                <ElDatePicker
                  v-model="publishForm.effectiveFrom"
                  class="w-full"
                  placeholder="发布时间"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                />
                <ElInput
                  v-model="publishForm.remark"
                  class="mt-3"
                  maxlength="200"
                  placeholder="发布说明"
                  show-word-limit
                  type="textarea"
                />
                <ElButton
                  class="mt-3 w-full"
                  :disabled="!canPublish"
                  :loading="configMutationLoading"
                  type="primary"
                  @click="publishRelease"
                >
                  创建发布版本
                </ElButton>
                <ElAlert
                  class="mt-3"
                  :closable="false"
                  title="发布请求仅提交已校验版本 ID，最终校验、校验和和原子发布由后端完成。"
                  type="info"
                />
              </ElCard>

              <ElCard class="min-h-0" shadow="never">
                <template #header>
                  <div class="flex flex-wrap items-center justify-between gap-3">
                    <span class="panel-title">发布历史</span>
                    <div class="flex gap-2">
                      <ElSelect
                        v-model="configReleaseQuery.status"
                        clearable
                        placeholder="发布状态"
                      >
                        <ElOption label="已发布" value="PUBLISHED" />
                        <ElOption label="已回滚" value="ROLLED_BACK" />
                        <ElOption label="已停用" value="RETIRED" />
                      </ElSelect>
                      <ElButton
                        :loading="configReleasesLoading"
                        type="primary"
                        @click="searchReleases"
                      >
                        <Search class="mr-1 size-4" />查询
                      </ElButton>
                    </div>
                  </div>
                </template>
                <ElTable
                  v-loading="configReleasesLoading"
                  :data="configReleases"
                  height="460"
                  row-key="id"
                  stripe
                >
                  <ElTableColumn type="expand">
                    <template #default="{ row }">
                      <div class="release-items">
                        <ElTag
                          v-for="item in row.items"
                          :key="`${row.id}-${item.configCode}`"
                          effect="plain"
                        >
                          {{ quantConfigGroupLabel(item.configCode) }} v{{ item.configVersion }}
                        </ElTag>
                      </div>
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="发布版本" min-width="110">
                    <template #default="{ row }">v{{ row.releaseVersion }}</template>
                  </ElTableColumn>
                  <ElTableColumn label="状态" min-width="110">
                    <template #default="{ row }">
                      <ElTag :type="quantConfigStatusMeta(row.status).type" effect="plain">
                        {{ quantConfigStatusMeta(row.status).label }}
                      </ElTag>
                    </template>
                  </ElTableColumn>
                  <ElTableColumn label="校验和" min-width="220" prop="checksum" show-overflow-tooltip />
                  <ElTableColumn label="生效时间" min-width="170" prop="effectiveFrom" />
                  <ElTableColumn label="发布人" min-width="110" prop="createdBy" />
                  <ElTableColumn label="说明" min-width="180" prop="remark" show-overflow-tooltip />
                  <ElTableColumn fixed="right" label="操作" min-width="110">
                    <template #default="{ row }">
                      <ElPopconfirm
                        title="回滚会创建新的更高发布版本，确认继续？"
                        @confirm="rollbackRelease(row)"
                      >
                        <template #reference>
                          <ElButton
                            :disabled="!canRollback"
                            link
                            type="warning"
                          >
                            <Undo2 class="mr-1 size-4" />回滚
                          </ElButton>
                        </template>
                      </ElPopconfirm>
                    </template>
                  </ElTableColumn>
                  <template #empty>
                    <ElEmpty description="暂无发布历史" />
                  </template>
                </ElTable>
                <div class="mt-4 flex justify-end">
                  <ElPagination
                    v-model:current-page="configReleaseQuery.pageNum"
                    v-model:page-size="configReleaseQuery.pageSize"
                    :page-sizes="[10, 20, 50]"
                    :total="configReleasesTotal"
                    background
                    layout="total, sizes, prev, pager, next"
                    @current-change="changeReleasePage"
                    @size-change="changeReleasePageSize"
                  />
                </div>
              </ElCard>
            </div>
          </ElTabPane>
        </ElTabs>
      </div>
    </div>

    <ElDrawer v-model="diffDrawerVisible" size="560px" title="字段级 Diff">
      <div v-if="configDiff?.changes.length" class="flex flex-col gap-3">
        <div
          v-for="change in configDiff.changes"
          :key="`${change.configCode}-${change.fieldPath}-${change.type}`"
          class="diff-card"
        >
          <div class="flex items-center justify-between gap-3">
            <strong>{{ change.fieldPath }}</strong>
            <ElTag effect="plain">{{ change.type }}</ElTag>
          </div>
          <div class="mt-3 grid gap-3 md:grid-cols-2">
            <div>
              <span class="diff-label">Before</span>
              <pre>{{ stringifyJson(change.before) }}</pre>
            </div>
            <div>
              <span class="diff-label">After</span>
              <pre>{{ stringifyJson(change.after) }}</pre>
            </div>
          </div>
        </div>
      </div>
      <ElEmpty v-else description="暂无差异" />
    </ElDrawer>
  </Page>
</template>

<style scoped>
.fund-config-page {
  --fund-amber: #b45309;
  --fund-ink: #14213d;
  --fund-teal: #0f766e;
}

.config-header {
  align-items: end;
  background: #ffffff;
  border: 1px solid rgb(148 163 184 / 22%);
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  min-height: 132px;
  padding: 24px 28px;
}

.config-header h1 {
  color: var(--fund-ink);
  font-family: 'Songti SC', 'Noto Serif CJK SC', serif;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.2;
  margin: 4px 0 9px;
}

.config-health {
  align-items: end;
  display: flex;
  flex-direction: column;
}

.panel-title {
  color: var(--fund-ink);
  font-size: 15px;
  font-weight: 700;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.group-item {
  background: #f8fafc;
  border: 1px solid rgb(148 163 184 / 22%);
  border-radius: 8px;
  cursor: pointer;
  display: grid;
  gap: 8px;
  padding: 14px;
  text-align: left;
  transition:
    background-color 160ms ease,
    border-color 160ms ease;
}

.group-item.active {
  background: #ecfdf5;
  border-color: rgb(15 118 110 / 40%);
}

.group-item span {
  color: var(--fund-ink);
  font-weight: 700;
}

.group-item small,
.release-note {
  color: #64748b;
}

.field-editor {
  border: 1px solid rgb(148 163 184 / 22%);
  border-radius: 8px;
  padding: 12px;
}

.field-editor-head,
.field-row {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(150px, 1fr) 120px minmax(220px, 2fr) 58px;
}

.field-editor-head {
  color: #64748b;
  font-size: 12px;
  margin-bottom: 8px;
}

.field-row + .field-row {
  margin-top: 10px;
}

.json-preview {
  background: #0f172a;
  border-radius: 8px;
  color: #dbeafe;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  line-height: 1.65;
  margin: 0;
  min-height: 280px;
  overflow: auto;
  padding: 14px;
  white-space: pre-wrap;
}

.checksum {
  color: var(--fund-amber);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  word-break: break-all;
}

.compact-empty {
  padding: 12px 0;
}

.release-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 56px;
}

.diff-card {
  border: 1px solid rgb(148 163 184 / 22%);
  border-radius: 8px;
  padding: 14px;
}

.diff-label {
  color: #64748b;
  display: block;
  font-size: 12px;
  margin-bottom: 6px;
}

.diff-card pre {
  background: #f8fafc;
  border-radius: 8px;
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  margin: 0;
  max-height: 180px;
  overflow: auto;
  padding: 10px;
  white-space: pre-wrap;
}

:deep(.el-tabs),
:deep(.el-tabs__content),
:deep(.el-tab-pane) {
  min-height: 0;
}

@media (max-width: 768px) {
  .config-header {
    align-items: start;
    flex-direction: column;
    gap: 18px;
    padding: 20px;
  }

  .config-health {
    align-items: start;
  }

  .field-editor-head {
    display: none;
  }

  .field-row {
    grid-template-columns: 1fr;
  }
}
</style>
