package org.dromara.fund.domain.dto;

import lombok.Data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 新建量化任务固定的配置血缘；后续模型任务和结果必须原样保存这些字段。 */
@Data
public class QuantConfigTaskContext {
    private Long configReleaseVersion;
    private String configReleaseChecksum;
    private Map<String, QuantConfigReleaseReference.GroupReference> groups = Map.of();

    public Map<String, QuantConfigReleaseReference.GroupReference> getGroups() {
        return snapshotGroups(groups);
    }

    public void setGroups(Map<String, QuantConfigReleaseReference.GroupReference> groups) {
        this.groups = snapshotGroups(groups);
    }

    private Map<String, QuantConfigReleaseReference.GroupReference> snapshotGroups(
        Map<String, QuantConfigReleaseReference.GroupReference> sourceGroups
    ) {
        if (sourceGroups == null || sourceGroups.isEmpty()) {
            return Map.of();
        }
        Map<String, QuantConfigReleaseReference.GroupReference> snapshot = new LinkedHashMap<>();
        sourceGroups.forEach((configCode, source) -> {
            if (source == null) {
                snapshot.put(configCode, null);
                return;
            }
            QuantConfigReleaseReference.GroupReference group = new QuantConfigReleaseReference.GroupReference();
            group.setConfigVersion(source.getConfigVersion());
            group.setSchemaVersion(source.getSchemaVersion());
            group.setChecksum(source.getChecksum());
            snapshot.put(configCode, group);
        });
        return Collections.unmodifiableMap(snapshot);
    }
}
