#!/usr/bin/env bash

# Detect only high-confidence violations. Business semantics remain a human review concern.
set -euo pipefail

usage() {
    echo "Usage: $0 [--changed] [repository-root]" >&2
}

changed_only=false
repository_root=""

while (($# > 0)); do
    case "$1" in
        --changed)
            changed_only=true
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        -*)
            usage
            exit 2
            ;;
        *)
            if [[ -n "$repository_root" ]]; then
                usage
                exit 2
            fi
            repository_root="$1"
            ;;
    esac
    shift
done

repository_root="${repository_root:-.}"
if [[ ! -d "$repository_root" ]]; then
    echo "Repository root does not exist: $repository_root" >&2
    exit 2
fi
if ! command -v rg >/dev/null 2>&1; then
    echo "rg is required to run this check." >&2
    exit 2
fi

repository_root="$(cd "$repository_root" && pwd -P)"
candidate_file="$(mktemp "${TMPDIR:-/tmp}/java-guidelines-files.XXXXXX")"
trap 'rm -f "$candidate_file"' EXIT

append_candidate() {
    local relative_path="$1"
    case "$relative_path" in
        *.java|*Mapper.xml)
            if [[ -f "$repository_root/$relative_path" ]]; then
                printf '%s\n' "$repository_root/$relative_path" >> "$candidate_file"
            fi
            ;;
    esac
}

if [[ "$changed_only" == true ]]; then
    if ! git -C "$repository_root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        echo "--changed requires a Git worktree: $repository_root" >&2
        exit 2
    fi

    {
        if git -C "$repository_root" rev-parse --verify HEAD >/dev/null 2>&1; then
            git -C "$repository_root" diff --name-only --diff-filter=ACMR HEAD
        else
            git -C "$repository_root" diff --name-only --diff-filter=ACMR
        fi
        git -C "$repository_root" diff --cached --name-only --diff-filter=ACMR
        git -C "$repository_root" ls-files --others --exclude-standard
    } | sort -u | while IFS= read -r relative_path; do
        append_candidate "$relative_path"
    done
else
    rg --files "$repository_root" \
        -g '*.java' \
        -g '*Mapper.xml' \
        -g '!**/target/**' \
        -g '!**/.git/**' \
        -g '!**/build/**' \
        -g '!**/.gradle/**' \
        | sort -u > "$candidate_file"
fi

if [[ ! -s "$candidate_file" ]]; then
    echo "Java guideline scan: no Java or Mapper XML files selected."
    exit 0
fi

error_count=0
warning_count=0

report_matches() {
    local level="$1"
    local rule="$2"
    local file="$3"
    local pattern="$4"
    local matches

    matches="$(rg -n --pcre2 "$pattern" "$file" || true)"
    [[ -z "$matches" ]] && return

    while IFS= read -r match; do
        printf '%s [%s] %s:%s\n' "$level" "$rule" "$file" "$match"
    done <<< "$matches"

    if [[ "$level" == "ERROR" ]]; then
        ((error_count += 1))
    else
        ((warning_count += 1))
    fi
}

while IFS= read -r file; do
    case "$file" in
        *.java)
            case "$file" in
                */controller/*|*Controller.java)
                    report_matches "ERROR" "controller-persistence-import" "$file" \
                        '^\s*import\s+[A-Za-z0-9_.]+\.(?:mapper|repository|dao)\.'
                    report_matches "ERROR" "controller-transaction" "$file" \
                        '^\s*@Transactional(?:\s|\(|$)'
                    report_matches "ERROR" "controller-map-request" "$file" \
                        '@RequestBody\s+(?:final\s+)?(?:java\.util\.)?Map\s*<'
                    ;;
            esac
            report_matches "WARN" "sensitive-log" "$file" \
                '(?i)^\s*(?!//).*?\blog\.(?:trace|debug|info|warn|error)\s*\([^;\n]*,\s*[^;\n]*\b(?:password|passwd|token|secret|accesskey|authorization)[A-Za-z0-9_]*\b'
            ;;
        *Mapper.xml)
            report_matches "ERROR" "mapper-select-star" "$file" '(?i)\bselect\s+\*'
            report_matches "WARN" "mapper-raw-substitution" "$file" '\$\{[^}]+\}'
            ;;
    esac
done < "$candidate_file"

printf 'Java guideline scan: %d error rule(s), %d warning rule(s).\n' "$error_count" "$warning_count"
((error_count == 0))
