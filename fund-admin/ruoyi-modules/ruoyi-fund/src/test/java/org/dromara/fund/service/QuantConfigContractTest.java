package org.dromara.fund.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.fund.client.FundEstimateProviderClient;
import org.dromara.fund.constant.FundCacheConstants;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 配置结构和规范化规则必须在发布前稳定，不依赖数据库或 Redis。 */
@Tag("dev")
final class QuantConfigContractTest {
    private static final Pattern SEED_ENTRY_PATTERN = Pattern.compile(
        "\\(\\d+,\\s*'([A-Z_]+)',\\s*1,\\s*1,\\s*'DRAFT',\\s*'([^']+)'::jsonb,\\s*'([0-9a-f]{64})'"
    );

    private final QuantConfigJsonSupport jsonSupport = new QuantConfigJsonSupport(new ObjectMapper());
    private final QuantConfigValidator validator = new QuantConfigValidator();

    @Test
    void canonicalJsonShouldSortObjectKeysAndPreserveArrayOrder() {
        String canonical = jsonSupport.canonicalize(jsonSupport.readObject("{\"z\":[2,1],\"a\":{\"b\":1,\"a\":2}}"));
        assertEquals("{\"a\":{\"a\":2,\"b\":1},\"z\":[2,1]}", canonical);
        assertEquals(jsonSupport.checksum(canonical), jsonSupport.checksum(canonical));
    }

    @Test
    void rsiMacdAndBacktestRulesShouldRejectInvalidSemantics() {
        List<String> macdErrors = validator.validate("RSI_MACD", 1, jsonSupport.readObject(
            "{\"rsi_period\":14,\"rsi_oversold\":70,\"rsi_overbought\":30,\"macd_fast\":26,\"macd_slow\":12,\"macd_signal\":9}"));
        List<String> backtestErrors = validator.validate("BACKTEST", 1, jsonSupport.readObject(
            "{\"fee_rate_percent\":0.1,\"slippage_percent\":0.01,\"win_rate\":0.5}"));

        assertFalse(macdErrors.isEmpty());
        assertTrue(backtestErrors.stream().anyMatch(error -> error.contains("D-010")));
    }

    @Test
    void unsupportedSchemaShouldFailWithoutFallback() {
        assertFalse(validator.validate("TREND", 2, jsonSupport.readObject("{\"windows\":[20,60]}"))
            .isEmpty());
    }

    @Test
    void estimateSchemaV2MustCarryExplicitFieldPrecisionWithoutChangingV1() {
        assertTrue(validator.validate("ESTIMATE", 2, jsonSupport.readObject(
            "{\"max_quote_age_seconds\":90,\"min_holding_coverage_percent\":60,"
                + "\"nav_decimal_scale\":6,\"percentage_decimal_scale\":4}"
        )).isEmpty());
        assertFalse(validator.validate("ESTIMATE", 1, jsonSupport.readObject(
            "{\"max_quote_age_seconds\":90,\"min_holding_coverage_percent\":60,"
                + "\"nav_decimal_scale\":6,\"percentage_decimal_scale\":4}"
        )).isEmpty());
        assertFalse(validator.validate("ESTIMATE", 2, jsonSupport.readObject(
            "{\"max_quote_age_seconds\":90,\"min_holding_coverage_percent\":60,"
                + "\"nav_decimal_scale\":6}"
        )).isEmpty());
    }

    @Test
    void d011SeedMustBeChecksumCorrectAndAcceptedByJavaSchema() throws IOException {
        Matcher matcher = SEED_ENTRY_PATTERN.matcher(Files.readString(locateSeedFile()));
        Set<String> codes = new HashSet<>();
        int count = 0;
        while (matcher.find()) {
            String configCode = matcher.group(1);
            String configJson = matcher.group(2);
            String checksum = matcher.group(3);
            assertEquals(checksum, jsonSupport.checksum(jsonSupport.canonicalize(jsonSupport.readObject(configJson))));
            assertTrue(validator.validate(configCode, 1, jsonSupport.readObject(configJson)).isEmpty(), configCode);
            codes.add(configCode);
            count++;
        }
        assertEquals(10, count);
        assertEquals(Set.of(
            "GLOBAL_CONVENTIONS", "ESTIMATE", "TREND", "MOVING_AVERAGE", "RSI_MACD",
            "NAV_POSITION", "FACTOR", "FUND_RISK", "PORTFOLIO_RISK", "BACKTEST"
        ), codes);
    }

    @Test
    void validatorsMustRejectEachD011GroupWhenItsCriticalInvariantIsBroken() throws IOException {
        assertInvalid("GLOBAL_CONVENTIONS", root -> ((ObjectNode) root.get("market_conventions")).remove("US"));
        assertInvalid("ESTIMATE", root -> root.put("max_quote_age_seconds", 0));
        assertInvalid("TREND", root -> root.putArray("windows").add(60).add(20));
        assertInvalid("MOVING_AVERAGE", root -> root.putArray("windows").add(20).add(60));
        assertInvalid("RSI_MACD", root -> root.put("macd_fast", 26));
        assertInvalid("NAV_POSITION", root -> root.put("min_sample_size", 757));
        assertInvalid("FACTOR", root -> ((ObjectNode) root.get("weights")).put("trend", 16));
        assertInvalid("FUND_RISK", root -> root.put("min_sample_size", 253));
        assertInvalid("PORTFOLIO_RISK", root -> root.put("var_confidence", 1));
        assertInvalid("BACKTEST", root -> ((ObjectNode) root.get("market_costs")).remove("HK"));
    }

    @Test
    void validatorsMustRejectOutOfRangeThresholdsAndMissingRiskUnits() throws IOException {
        assertInvalid("MOVING_AVERAGE", root -> root.put("deviation_threshold_percent", 101));
        assertInvalid("NAV_POSITION", root -> root.putArray("region_thresholds").add(20).add(20).add(90));
        assertInvalid("FUND_RISK", root -> root.put("risk_unit", ""));
        assertInvalid("PORTFOLIO_RISK", root -> root.put("max_missing_percent", 101));
    }

    @Test
    void estimateProviderMustRequireAndIsolateExactReleaseContext() throws NoSuchMethodException {
        String checksum = "a".repeat(64);

        assertEquals(
            "fund:estimate:000001:holding-estimate-v1:7:" + checksum,
            FundCacheConstants.estimateCacheKey("000001", "holding-estimate-v1", 7L, checksum)
        );
        assertEquals(
            "fund:lock:estimate:000001",
            FundCacheConstants.estimateLockKey("000001")
        );
        assertEquals(
            QuantConfigTaskContext.class,
            FundEstimateProviderClient.class
                .getMethod("fetch", String.class, QuantConfigTaskContext.class)
                .getParameterTypes()[1]
        );
    }

    @Test
    void resultCacheKeysMustRemainSeparateForHistoricalReleaseRecalculation() {
        String v1Checksum = "a".repeat(64);
        String v2Checksum = "b".repeat(64);

        assertNotEquals(
            FundCacheConstants.estimateCacheKey("000001", "holding-estimate-v1", 1L, v1Checksum),
            FundCacheConstants.estimateCacheKey("000001", "holding-estimate-v2", 2L, v2Checksum)
        );
    }

    @Test
    void estimateSnapshotSchemaMustRetainReleaseLineageAcrossRecalculation() throws IOException {
        String baseline = Files.readString(locateProjectFile("script/sql/postgres/postgres_fund_quant_v1.sql"));
        String migration = Files.readString(locateProjectFile("script/sql/update/postgres/update_quant_config_center_v1.sql"));

        assertTrue(baseline.contains("UNIQUE (fund_code, estimate_time, config_release_version)"));
        assertTrue(baseline.contains("FOREIGN KEY (config_release_version) REFERENCES quant_config_release (release_version)"));
        assertTrue(migration.contains("UNIQUE (fund_code, estimate_time, config_release_version)"));
        assertTrue(migration.contains("FOREIGN KEY (config_release_version) REFERENCES quant_config_release (release_version) NOT VALID"));
    }

    @Test
    void postgresSchemaMustProtectConfigImmutabilityAndExcludeGlobalTablesFromTenantFilters() throws IOException {
        String migration = Files.readString(locateProjectFile("script/sql/update/postgres/update_quant_config_center_v1.sql"));
        String application = Files.readString(locateProjectFile("ruoyi-admin/src/main/resources/application.yml"));

        assertTrue(migration.contains("CONSTRAINT uk_quant_config_version UNIQUE (config_code, config_version)"));
        assertTrue(migration.contains("CONSTRAINT uk_quant_config_release_version UNIQUE (release_version)"));
        assertTrue(migration.contains("CONSTRAINT uk_quant_config_release_item UNIQUE (release_id, config_code)"));
        assertTrue(migration.contains("CREATE TRIGGER trg_quant_config_version_immutable"));
        assertTrue(migration.contains("CREATE TRIGGER trg_quant_config_release_immutable"));
        assertTrue(migration.contains("CREATE TRIGGER trg_quant_config_release_item_immutable"));
        assertTrue(migration.contains("GRANT SELECT ON quant_config_version, quant_config_release, quant_config_release_item TO fund_quant_reader"));
        assertTrue(application.contains("- quant_config_version"));
        assertTrue(application.contains("- quant_config_release"));
        assertTrue(application.contains("- quant_config_release_item"));
    }

    @Test
    void sharedFixtureMustKeepJavaCanonicalizationReleaseChecksumAndValidationPayloadsStable() throws IOException {
        JsonNode fixture = new ObjectMapper().readTree(Files.readString(locateProjectFile("spec/fixtures/quant-config-v1-contract.json")));
        JsonNode canonical = fixture.path("canonical_json");
        assertEquals(canonical.path("canonical").asText(), jsonSupport.canonicalize(canonical.path("value")));
        assertEquals(canonical.path("checksum").asText(), jsonSupport.checksum(canonical.path("canonical").asText()));

        List<String> parts = new ArrayList<>();
        for (JsonNode item : fixture.path("release_checksum").path("items")) {
            parts.add(item.path("config_code").asText() + ":" + item.path("config_version").asInt()
                + ":" + item.path("checksum").asText());
        }
        assertEquals(fixture.path("release_checksum").path("checksum").asText(),
            jsonSupport.checksum(parts.stream().sorted().collect(java.util.stream.Collectors.joining("\n"))));

        for (JsonNode testCase : fixture.path("validation_cases")) {
            String configCode = testCase.path("config_code").asText();
            ObjectNode config = seedConfig(configCode);
            testCase.path("replace").fields().forEachRemaining(entry -> config.set(entry.getKey(), entry.getValue()));
            List<String> expectedErrors = new ArrayList<>();
            testCase.path("expected_errors").forEach(error -> expectedErrors.add(error.asText()));
            assertEquals(expectedErrors, validator.validate(configCode, 1, config), configCode);
        }
    }

    private Path locateSeedFile() {
        return locateProjectFile("script/sql/update/postgres/seed_quant_config_cross_market_v1.sql");
    }

    private void assertInvalid(String configCode, Consumer<ObjectNode> mutation) throws IOException {
        ObjectNode root = seedConfig(configCode);
        mutation.accept(root);
        assertFalse(validator.validate(configCode, 1, root).isEmpty(), configCode);
    }

    private ObjectNode seedConfig(String configCode) throws IOException {
        Matcher matcher = SEED_ENTRY_PATTERN.matcher(Files.readString(locateSeedFile()));
        while (matcher.find()) {
            if (configCode.equals(matcher.group(1))) {
                return (ObjectNode) jsonSupport.readObject(matcher.group(2));
            }
        }
        throw new IllegalArgumentException("种子中缺少配置分组: " + configCode);
    }

    private Path locateProjectFile(String relativePath) {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("未找到量化配置种子文件");
    }
}
