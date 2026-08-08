import json
import re
from pathlib import Path

import pytest

from app.core.config import Settings
from app.core.exceptions import (
    QuantConfigChecksumMismatchError,
    QuantConfigNotPublishedError,
    QuantConfigSchemaUnsupportedError,
    QuantConfigVersionMismatchError,
)
from app.repositories.quant_config_repository import (
    QuantConfigRepository,
    canonicalize_json,
    sha256,
    validate_schema,
    validate_schema_v1,
)
from app.schemas.quant_config import SUPPORTED_CONFIG_CODES, QuantConfigValidationItem


SEED_FILE = Path(__file__).resolve().parents[2] / "fund-admin/script/sql/update/postgres/seed_quant_config_cross_market_v1.sql"
CONTRACT_FIXTURE = Path(__file__).resolve().parents[2] / "spec/fixtures/quant-config-v1-contract.json"
SEED_ENTRY_PATTERN = re.compile(
    r"\(\d+,\s*'([A-Z_]+)',\s*1,\s*1,\s*'DRAFT',\s*'([^']+)'::jsonb,\s*'([0-9a-f]{64})'",
    re.DOTALL,
)


class _Cursor:
    def __init__(self, rows: list[dict]) -> None:
        self.commands: list[str] = []
        self.rows = rows

    def __enter__(self) -> "_Cursor":
        return self

    def __exit__(self, *_: object) -> None:
        return None

    def execute(self, command: str, *_: object) -> None:
        self.commands.append(command)

    def fetchall(self) -> list[dict]:
        return self.rows


class _Connection:
    def __init__(self, rows: list[dict]) -> None:
        self.cursor_instance = _Cursor(rows)

    def __enter__(self) -> "_Connection":
        return self

    def __exit__(self, *_: object) -> None:
        return None

    def transaction(self) -> "_Connection":
        return self

    def cursor(self, **_: object) -> _Cursor:
        return self.cursor_instance


class _Pool:
    def __init__(self, rows: list[dict]) -> None:
        self.connection_instance = _Connection(rows)
        self.calls = 0

    def connection(self) -> _Connection:
        self.calls += 1
        return self.connection_instance


class _FailingPool:
    def connection(self) -> _Connection:
        raise RuntimeError("database unavailable")


def _release_rows() -> tuple[list[dict], str]:
    rows = []
    for index, code in enumerate(sorted(SUPPORTED_CONFIG_CODES), start=1):
        config = {"code": code}
        checksum = sha256(canonicalize_json(config))
        rows.append(
            {
                "release_checksum": "",
                "config_code": code,
                "config_version": index,
                "schema_version": 1,
                "config_checksum": checksum,
                "config_json": json.dumps(config),
                "stored_checksum": checksum,
            }
        )
    release_checksum = sha256(
        "\n".join(
            f"{row['config_code']}:{row['config_version']}:{row['config_checksum']}"
            for row in rows
        )
    )
    for row in rows:
        row["release_checksum"] = release_checksum
    return rows, release_checksum


def test_canonical_json_sorts_object_keys_and_preserves_array_order() -> None:
    assert canonicalize_json({"z": [2, 1], "a": {"b": 1, "a": 2}}) == '{"a":{"a":2,"b":1},"z":[2,1]}'


def test_checksum_is_stable_for_equivalent_json_objects() -> None:
    left = canonicalize_json(json.loads('{"b": 2, "a": 1}'))
    right = canonicalize_json(json.loads('{"a": 1, "b": 2}'))
    assert sha256(left) == sha256(right)


def test_backtest_rejects_win_rate_before_d010_is_adopted() -> None:
    assert validate_schema_v1(
        "BACKTEST",
        {"fee_rate_percent": 0.1, "slippage_percent": 0.01, "win_rate": 0.5},
    )


def test_estimate_schema_v2_requires_explicit_field_precision() -> None:
    config = {
        "max_quote_age_seconds": 90,
        "min_holding_coverage_percent": 60,
        "nav_decimal_scale": 6,
        "percentage_decimal_scale": 4,
    }

    assert validate_schema("ESTIMATE", 2, config) == []
    assert validate_schema("ESTIMATE", 1, config) == [
        "ESTIMATE: unknown fields nav_decimal_scale,percentage_decimal_scale"
    ]
    assert validate_schema("ESTIMATE", 2, {**config, "percentage_decimal_scale": 0}) == [
        "ESTIMATE: percentage_decimal_scale must be a positive integer"
    ]


def test_settings_reads_documented_readonly_dsn_environment_variable(monkeypatch: pytest.MonkeyPatch) -> None:
    documented_dsn = "postgresql://fund_quant_reader@localhost:5432/fund_quant"
    monkeypatch.setenv("FUND_QUANT_CONFIG_READONLY_DSN", documented_dsn)

    assert Settings().quant_config_readonly_dsn == documented_dsn


def test_validation_item_checksum_uses_canonical_json() -> None:
    config = {"windows": [20, 60]}
    item = QuantConfigValidationItem(
        configCode="TREND",
        configVersion=1,
        schemaVersion=1,
        configJson=json.dumps(config),
        checksum=sha256(canonicalize_json(config)),
    )
    assert item.checksum == sha256('{"windows":[20,60]}')


def test_d011_seed_is_checksum_correct_and_accepted_by_python_schema() -> None:
    entries = SEED_ENTRY_PATTERN.findall(SEED_FILE.read_text(encoding="utf-8"))

    assert len(entries) == len(SUPPORTED_CONFIG_CODES)
    assert {code for code, _, _ in entries} == SUPPORTED_CONFIG_CODES
    for config_code, config_json, checksum in entries:
        config = json.loads(config_json)
        assert sha256(canonicalize_json(config)) == checksum
        assert validate_schema_v1(config_code, config) == []


def test_shared_fixture_keeps_python_canonicalization_release_checksum_and_validation_payloads_stable() -> None:
    fixture = json.loads(CONTRACT_FIXTURE.read_text(encoding="utf-8"))
    canonical = fixture["canonical_json"]
    assert canonicalize_json(canonical["value"]) == canonical["canonical"]
    assert sha256(canonical["canonical"]) == canonical["checksum"]

    release_parts = sorted(
        f"{item['config_code']}:{item['config_version']}:{item['checksum']}"
        for item in fixture["release_checksum"]["items"]
    )
    assert sha256("\n".join(release_parts)) == fixture["release_checksum"]["checksum"]

    seed_configs = {
        config_code: json.loads(config_json)
        for config_code, config_json, _ in SEED_ENTRY_PATTERN.findall(SEED_FILE.read_text(encoding="utf-8"))
    }
    for case in fixture["validation_cases"]:
        config = json.loads(json.dumps(seed_configs[case["config_code"]]))
        config.update(case["replace"])
        assert validate_schema_v1(case["config_code"], config) == case["expected_errors"]


def test_exact_release_cache_never_substitutes_another_checksum() -> None:
    rows, release_checksum = _release_rows()
    pool = _Pool(rows)
    repository = QuantConfigRepository(Settings(quant_config_readonly_dsn="postgresql://readonly"), pool=pool)

    first = repository.load_release(7, release_checksum)
    second = repository.load_release(7, release_checksum)

    assert first is second
    assert pool.calls == 1
    assert "SET TRANSACTION READ ONLY" in pool.connection_instance.cursor_instance.commands
    with pytest.raises(QuantConfigChecksumMismatchError):
        repository.load_release(7, "b" * 64)


def test_missing_release_and_database_failure_never_fallback_to_another_release() -> None:
    settings = Settings(quant_config_readonly_dsn="postgresql://readonly")

    with pytest.raises(QuantConfigNotPublishedError):
        QuantConfigRepository(settings, pool=_Pool([])).load_release(7, "a" * 64)
    with pytest.raises(RuntimeError, match="database unavailable"):
        QuantConfigRepository(settings, pool=_FailingPool()).load_release(7, "a" * 64)


def test_release_rejects_mismatched_item_and_unsupported_schema() -> None:
    rows, release_checksum = _release_rows()
    rows[0]["stored_checksum"] = "f" * 64
    with pytest.raises(QuantConfigVersionMismatchError):
        QuantConfigRepository(
            Settings(quant_config_readonly_dsn="postgresql://readonly"),
            pool=_Pool(rows),
        ).load_release(7, release_checksum)

    rows, release_checksum = _release_rows()
    rows[0]["schema_version"] = 2
    with pytest.raises(QuantConfigSchemaUnsupportedError):
        QuantConfigRepository(
            Settings(quant_config_readonly_dsn="postgresql://readonly"),
            pool=_Pool(rows),
        ).load_release(7, release_checksum)
