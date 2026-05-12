#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "사용법: ./scripts/new-exec-plan.sh <topic> <owner>"
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

topic_raw="$1"
owner_raw="$2"

slugify() {
  local value="$1"
  value="$(printf "%s" "$value" | tr '[:upper:]' '[:lower:]')"
  value="$(printf "%s" "$value" | sed -E 's/[[:space:]]+/-/g; s/[^0-9a-z가-힣_-]+/-/g; s/-+/-/g; s/^-|-$//g')"
  printf "%s" "$value"
}

topic="$(slugify "$topic_raw")"
owner="$(slugify "$owner_raw")"

if [[ -z "$topic" || -z "$owner" ]]; then
  echo "topic/owner는 비어 있을 수 없습니다."
  exit 1
fi

timestamp="$(date +"%Y-%m-%d-%H%M%S")"
out_path="docs/exec-plans/active/${timestamp}-${topic}-${owner}.md"
mkdir -p "$(dirname "$out_path")"

if [[ -e "$out_path" ]]; then
  echo "이미 같은 이름의 파일이 있습니다: $out_path"
  exit 1
fi

cat > "$out_path" <<PLAN
# ${timestamp}-${topic}-${owner}

- 작성자: ${owner}
- 상태: 진행 중
- 생성시각(로컬): $(date +"%Y-%m-%d %H:%M:%S")

## 배경

## 목표

## 범위

## 비범위

## 단계
1.
2.
3.

## 위험 및 롤백

## 검증
- [ ] 빌드
- [ ] 테스트
- [ ] 계약 검사

## 결정 로그
- $(date +"%Y-%m-%d %H:%M:%S"):
PLAN

echo "실행 계획 파일을 생성했습니다: $out_path"
