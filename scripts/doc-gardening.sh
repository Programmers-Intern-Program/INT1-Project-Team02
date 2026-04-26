#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

REPORT_PATH="docs/generated/doc-gardening-report.md"
STALE_DAYS="${STALE_DAYS:-14}"
NOW_EPOCH="$(date +%s)"
TODAY="$(date +%F)"
active_pattern='^docs/exec-plans/active/[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{6}-[^/]+-[^/]+\.md$'
completed_pattern='^docs/exec-plans/completed/[0-9]{4}-[0-9]{2}/[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{6}-[^/]+-[^/]+\.md$'
impl_author_pattern='^docs/impl/[a-z0-9]+(-[a-z0-9]+)*$'
impl_file_pattern='^docs/impl/[a-z0-9]+(-[a-z0-9]+)*/[0-9]{4}-[a-z0-9]+(-[a-z0-9]+)*\.md$'

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
  "docs/impl/README.md"
  "docs/references/impl-record-template.md"
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
invalid_active_entries=()
invalid_completed_entries=()
invalid_impl_entries=()
impl_author_entries=()
unreviewed_impl_entries=()
missing_evidence_entries=()

shopt -s nullglob
for file in docs/exec-plans/active/*.md; do
  active_count=$((active_count + 1))

  if ! [[ "$file" =~ $active_pattern ]]; then
    invalid_active_entries+=("- \`$file\` (파일명 규칙 위반)")
  fi

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

while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  if ! [[ "$file" =~ $completed_pattern ]]; then
    invalid_completed_entries+=("- \`$file\` (경로/파일명 규칙 위반)")
  fi
done < <(find docs/exec-plans/completed -mindepth 1 -type f -name "*.md" | sort)

while IFS= read -r dir; do
  [[ -z "$dir" ]] && continue
  if ! [[ "$dir" =~ $impl_author_pattern ]]; then
    invalid_impl_entries+=("- \`$dir\` (작성자 폴더명 규칙 위반)")
    continue
  fi

  author="$(basename "$dir")"
  count="$(find "$dir" -maxdepth 1 -type f -name "*.md" | wc -l | tr -d ' ')"
  impl_author_entries+=("- \`$author\`: ${count}개")
done < <(find docs/impl -mindepth 1 -maxdepth 1 -type d | sort)

while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  if ! [[ "$file" =~ $impl_file_pattern ]]; then
    invalid_impl_entries+=("- \`$file\` (구현 기록 파일명 규칙 위반)")
  fi

  if [[ "$(head -n 1 "$file")" != "---" ]]; then
    invalid_impl_entries+=("- \`$file\` (frontmatter 누락)")
    continue
  fi

  for field in "generated-by:" "reviewed-by:" "reviewed-at:" "evidence:"; do
    if ! grep -q "^${field}" "$file"; then
      invalid_impl_entries+=("- \`$file\` (frontmatter 필드 누락: $field)")
    fi
  done

  generated_by="$(grep -m 1 "^generated-by:" "$file" | sed 's/^generated-by:[[:space:]]*//')"
  reviewed_by="$(grep -m 1 "^reviewed-by:" "$file" | sed 's/^reviewed-by:[[:space:]]*//')"
  evidence="$(grep -m 1 "^evidence:" "$file" | sed 's/^evidence:[[:space:]]*//')"

  if [[ "$generated_by" == "ai-draft" && -z "$reviewed_by" ]]; then
    unreviewed_impl_entries+=("- \`$file\`")
  fi

  if [[ -z "$evidence" ]]; then
    missing_evidence_entries+=("- \`$file\`")
  fi
done < <(find docs/impl -mindepth 2 -type f -name "*.md" | sort)

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
  echo "- active 파일명 규칙 위반 수: ${#invalid_active_entries[@]}"
  echo "- completed 파일명/경로 규칙 위반 수: ${#invalid_completed_entries[@]}"
  echo "- 구현 기록 규칙 위반 수: ${#invalid_impl_entries[@]}"
  echo "- 미검토 AI 구현 기록 수: ${#unreviewed_impl_entries[@]}"
  echo "- evidence 누락 구현 기록 수: ${#missing_evidence_entries[@]}"
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
  echo "## 실행계획 파일명 규칙 위반"
  if (( ${#invalid_active_entries[@]} == 0 && ${#invalid_completed_entries[@]} == 0 )); then
    echo "- 없음"
  else
    printf '%s\n' "${invalid_active_entries[@]}"
    printf '%s\n' "${invalid_completed_entries[@]}"
  fi
  echo
  echo "## 구현 기록 현황"
  if (( ${#impl_author_entries[@]} == 0 )); then
    echo "- 작성자별 구현 기록 없음"
  else
    printf '%s\n' "${impl_author_entries[@]}"
  fi
  echo
  echo "## 구현 기록 규칙 위반"
  if (( ${#invalid_impl_entries[@]} == 0 )); then
    echo "- 없음"
  else
    printf '%s\n' "${invalid_impl_entries[@]}"
  fi
  echo
  echo "## 미검토 AI 구현 기록"
  if (( ${#unreviewed_impl_entries[@]} == 0 )); then
    echo "- 없음"
  else
    printf '%s\n' "${unreviewed_impl_entries[@]}"
  fi
  echo
  echo "## evidence 누락 구현 기록"
  if (( ${#missing_evidence_entries[@]} == 0 )); then
    echo "- 없음"
  else
    printf '%s\n' "${missing_evidence_entries[@]}"
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
  echo "3. 구현 기록이 필요한 변경은 작성자별 \`docs/impl/{author}/\` 하위에 기록합니다."
  echo "4. 누락 문서는 우선순위에 따라 이번 스프린트에서 채웁니다."
} > "$REPORT_PATH"

echo "문서 가드닝 리포트를 생성했습니다: $REPORT_PATH"
