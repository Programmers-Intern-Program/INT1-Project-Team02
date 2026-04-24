#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

REPORT_PATH="docs/generated/doc-gardening-report.md"
STALE_DAYS="${STALE_DAYS:-14}"
NOW_EPOCH="$(date +%s)"
TODAY="$(date +%F)"

parse_epoch() {
  local date_str="$1"

  if epoch=$(date -d "$date_str" +%s 2>/dev/null); then
    echo "$epoch"
    return 0
  fi

  if epoch=$(date -j -f "%Y-%m-%d" "$date_str" +%s 2>/dev/null); then
    echo "$epoch"
    return 0
  fi

  return 1
}

required_docs=(
  "AGENTS.md"
  "ARCHITECTURE.md"
  "QUALITY_SCORE.md"
  "RELIABILITY.md"
  "SECURITY.md"
  "docs/README.md"
  "docs/exec-plans/tech-debt-tracker.md"
)

missing_docs=()
for file in "${required_docs[@]}"; do
  if [[ ! -f "$file" ]]; then
    missing_docs+=("$file")
  fi
done

active_count=0
stale_count=0
stale_entries=()

shopt -s nullglob
for file in docs/exec-plans/active/*.md; do
  active_count=$((active_count + 1))
  base_name="$(basename "$file" .md)"
  date_prefix="${base_name:0:10}"

  if epoch=$(parse_epoch "$date_prefix"); then
    age_days=$(((NOW_EPOCH - epoch) / 86400))
    if ((age_days >= STALE_DAYS)); then
      stale_count=$((stale_count + 1))
      stale_entries+=("- \`$file\` (${age_days}일 경과)")
    fi
  else
    stale_entries+=("- \`$file\` (파일명 날짜 파싱 실패)")
  fi
done
shopt -u nullglob

todo_lines="$(
  rg -n \
    --glob '!docs/generated/**' \
    "TODO:|FIXME:|XXX:" \
    docs AGENTS.md ARCHITECTURE.md QUALITY_SCORE.md RELIABILITY.md SECURITY.md 2>/dev/null || true
)"
todo_count="$(printf "%s\n" "$todo_lines" | sed '/^$/d' | wc -l | tr -d ' ')"

{
  echo "# 문서 가드닝 리포트"
  echo
  echo "- 생성일: $TODAY"
  echo "- 기준: active 실행 계획 ${STALE_DAYS}일 초과 시 점검 대상"
  echo
  echo "## 요약"
  echo "- active 실행 계획 수: $active_count"
  echo "- 오래된 active 계획 수: $stale_count"
  echo "- 문서 TODO/FIXME 수: $todo_count"
  echo "- 필수 문서 누락 수: ${#missing_docs[@]}"
  echo
  echo "## 오래된 실행 계획"
  if (( ${#stale_entries[@]} == 0 )); then
    echo "- 없음"
  else
    printf '%s\n' "${stale_entries[@]}"
  fi
  echo
  echo "## 필수 문서 누락"
  if (( ${#missing_docs[@]} == 0 )); then
    echo "- 없음"
  else
    for file in "${missing_docs[@]}"; do
      echo "- \`$file\`"
    done
  fi
  echo
  echo "## TODO/FIXME 샘플 (최대 20줄)"
  if [[ "$todo_count" == "0" ]]; then
    echo "- 없음"
  else
    echo '```text'
    printf "%s\n" "$todo_lines" | sed '/^$/d' | head -n 20
    echo '```'
  fi
  echo
  echo "## 권장 액션"
  echo '1. 오래된 active 계획은 완료/중단 여부를 확정하고 `completed/` 이동 또는 폐기합니다.'
  echo "2. 반복되는 TODO는 실행 계획으로 승격하거나 기술 부채 트래커에 등록합니다."
  echo "3. 누락 문서는 우선순위에 따라 이번 스프린트에서 채웁니다."
} > "$REPORT_PATH"

echo "문서 가드닝 리포트를 생성했습니다: $REPORT_PATH"
