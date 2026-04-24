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
  "docs/product-specs/index.md"
  "docs/references/internal-api-contracts.md"
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

echo "에이전트 문서 검사를 통과했습니다."
