#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

required_files=(
  "AGENTS.md"
  "CLAUDE.md"
  "ARCHITECTURE.md"
  "QUALITY_SCORE.md"
  "RELIABILITY.md"
  "SECURITY.md"
  "docs/README.md"
  "docs/design-docs/index.md"
  "docs/design-docs/core-beliefs.md"
  "docs/exec-plans/README.md"
  "docs/exec-plans/TEMPLATE.md"
  "docs/exec-plans/tech-debt-tracker.md"
  "docs/generated/db-schema.md"
  "docs/generated/doc-gardening-report.md"
  "docs/impl/README.md"
  "docs/product-specs/index.md"
  "docs/references/internal-api-contracts.md"
  "docs/references/impl-record-template.md"
  "scripts/new-exec-plan.sh"
  "scripts/doc-gardening.sh"
  ".github/workflows/doc-gardening.yml"
)

missing=0
for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "필수 파일이 없습니다: $file"
    missing=1
  fi
done

if [[ "$missing" -ne 0 ]]; then
  exit 1
fi

agents_lines=$(wc -l < AGENTS.md)
if [[ "$agents_lines" -gt 180 ]]; then
  echo "AGENTS.md가 너무 깁니다 ($agents_lines줄). 매뉴얼이 아니라 진입 지도로 유지하세요."
  exit 1
fi

if ! grep -q "docs/README.md" AGENTS.md; then
  echo "AGENTS.md에 docs/README.md 링크가 필요합니다."
  exit 1
fi

if ! grep -q "ARCHITECTURE.md" AGENTS.md; then
  echo "AGENTS.md에 ARCHITECTURE.md 링크가 필요합니다."
  exit 1
fi

if ! grep -q "/internal/v1/speech" docs/references/internal-api-contracts.md; then
  echo "docs/references/internal-api-contracts.md에 internal speech 계약이 없습니다."
  exit 1
fi

if ! grep -q "QUALITY_SCORE.md" AGENTS.md; then
  echo "AGENTS.md에 QUALITY_SCORE.md 링크가 필요합니다."
  exit 1
fi

if ! grep -q "tech-debt-tracker.md" AGENTS.md; then
  echo "AGENTS.md에 기술 부채 트래커 링크가 필요합니다."
  exit 1
fi

if ! grep -q "docs/impl/{author-kebab-case}/" AGENTS.md; then
  echo "AGENTS.md에 구현 기록 경로 규칙이 필요합니다."
  exit 1
fi

active_pattern='^docs/exec-plans/active/[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{6}-[^/]+-[^/]+\.md$'
completed_pattern='^docs/exec-plans/completed/[0-9]{4}-[0-9]{2}/[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{6}-[^/]+-[^/]+\.md$'
impl_author_pattern='^docs/impl/[a-z0-9]+(-[a-z0-9]+)*$'
impl_file_pattern='^docs/impl/[a-z0-9]+(-[a-z0-9]+)*/[0-9]{4}-[a-z0-9]+(-[a-z0-9]+)*\.md$'

while IFS= read -r path; do
  [[ -z "$path" ]] && continue
  if ! [[ "$path" =~ $active_pattern ]]; then
    echo "active 실행 계획 파일명 규칙 위반: $path"
    echo "규칙: YYYY-MM-DD-HHMMSS-topic-owner.md"
    exit 1
  fi
  if ! grep -q "^- 작성자:" "$path"; then
    echo "작성자 메타데이터가 없습니다: $path"
    exit 1
  fi
done < <(find docs/exec-plans/active -maxdepth 1 -type f -name "*.md" | sort)

if find docs/exec-plans/completed -maxdepth 1 -type f -name "*.md" | grep -q "."; then
  echo "completed 루트에 직접 .md 파일을 두지 마세요. completed/YYYY-MM/ 하위에 저장하세요."
  exit 1
fi

while IFS= read -r path; do
  [[ -z "$path" ]] && continue
  if ! [[ "$path" =~ $completed_pattern ]]; then
    echo "completed 실행 계획 파일명/경로 규칙 위반: $path"
    echo "규칙: completed/YYYY-MM/YYYY-MM-DD-HHMMSS-topic-owner.md"
    exit 1
  fi
  if ! grep -q "^- 상태: 완료(읽기 전용)" "$path"; then
    echo "completed 문서 상태 표기가 누락되었습니다(완료/읽기 전용): $path"
    exit 1
  fi
done < <(find docs/exec-plans/completed -mindepth 2 -type f -name "*.md" | sort)

while IFS= read -r path; do
  [[ -z "$path" ]] && continue
  if ! [[ "$path" =~ $impl_author_pattern ]]; then
    echo "구현 기록 작성자 폴더명 규칙 위반: $path"
    echo "규칙: docs/impl/{author-kebab-case}/"
    exit 1
  fi
done < <(find docs/impl -mindepth 1 -maxdepth 1 -type d | sort)

while IFS= read -r path; do
  [[ -z "$path" ]] && continue
  if ! [[ "$path" =~ $impl_file_pattern ]]; then
    echo "구현 기록 파일명 규칙 위반: $path"
    echo "규칙: docs/impl/{author-kebab-case}/{MMDD}-{feature-kebab-case}.md"
    exit 1
  fi

  first_line="$(head -n 1 "$path")"
  if [[ "$first_line" != "---" ]]; then
    echo "구현 기록 frontmatter가 없습니다: $path"
    exit 1
  fi

  for field in "generated-by:" "reviewed-by:" "reviewed-at:" "evidence:"; do
    if ! grep -q "^${field}" "$path"; then
      echo "구현 기록 frontmatter 필드가 없습니다: $path ($field)"
      exit 1
    fi
  done

  for field in "reviewed-by" "reviewed-at" "evidence"; do
    value="$(grep -m 1 "^${field}:" "$path" | sed "s/^${field}:[[:space:]]*//" | sed 's/[[:space:]]*#.*$//')"
    if [[ -z "$value" ]]; then
      echo "구현 기록 검토 필드가 비어 있습니다: $path ($field)"
      echo "AI 초안은 허용되지만 PR 병합 전 reviewed-by, reviewed-at, evidence를 채워야 합니다."
      exit 1
    fi
  done
done < <(find docs/impl -mindepth 2 -type f -name "*.md" | sort)

echo "에이전트 문서 검사를 통과했습니다."
