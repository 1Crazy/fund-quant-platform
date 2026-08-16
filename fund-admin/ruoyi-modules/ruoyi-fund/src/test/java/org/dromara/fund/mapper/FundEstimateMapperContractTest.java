package org.dromara.fund.mapper;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** fund_estimate 快照读取、去重与保留 SQL 的回归契约。 */
@Tag("dev")
final class FundEstimateMapperContractTest {

    @Test
    void latestSnapshotMustBeSelectedWithinTheExactReleaseLineage() {
        String mapper = mapper("mapper/fund/FundEstimateMapper.xml");

        assertTrue(mapper.contains("config_release_version = #{configReleaseVersion}"));
        assertTrue(mapper.contains("config_release_checksum = #{configReleaseChecksum}"));
        assertTrue(mapper.contains("ORDER BY estimate_time DESC"));
        assertTrue(mapper.contains("LIMIT 1"));
    }

    @Test
    void retentionMustKeepTheLatestSnapshotForEveryFundAndRelease() {
        String mapper = mapper("mapper/fund/FundEstimateMapper.xml");

        assertTrue(mapper.contains("PARTITION BY fund_code, config_release_version, config_release_checksum"));
        assertTrue(mapper.contains("snapshot_rank &gt; 1"));
        assertTrue(mapper.contains("estimate_time &lt; #{cutoff}"));
        assertTrue(mapper.contains("LIMIT #{batchSize}"));
    }

    @Test
    void schemaMustPreventDuplicateSnapshotsAndPersistStage3StatusFields() throws IOException {
        String baseline = projectFile("script/sql/postgres/postgres_fund_quant_v1.sql");
        String migration = projectFile("script/sql/update/postgres/update_harden_realtime_fund_estimation_v1.sql");

        assertTrue(baseline.contains("UNIQUE (fund_code, estimate_time, config_release_version)"));
        assertTrue(migration.contains("ADD COLUMN IF NOT EXISTS status_reason"));
        assertTrue(migration.contains("ADD COLUMN IF NOT EXISTS holding_coverage_rate"));
        assertTrue(migration.contains("ADD COLUMN IF NOT EXISTS quote_coverage_rate"));
        assertTrue(migration.contains("SET source_status = 'STALE'"));
        assertTrue(migration.contains("LEGACY_SNAPSHOT_METADATA_UNAVAILABLE"));
    }

    private String mapper(String resource) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("缺少 Mapper 资源: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("读取 Mapper 资源失败", error);
        }
    }

    private String projectFile(String relativePath) throws IOException {
        java.nio.file.Path directory = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            java.nio.file.Path candidate = directory.resolve(relativePath);
            if (java.nio.file.Files.isRegularFile(candidate)) {
                return java.nio.file.Files.readString(candidate);
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("找不到项目文件: " + relativePath);
    }
}
