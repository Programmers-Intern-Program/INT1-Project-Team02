# 실행 계획 문서

수시간 이상 걸리거나 단계가 많은 작업은 실행 계획 문서를 사용합니다.

## 폴더 규칙
- `active/`: 현재 진행 중인 계획
- `completed/`: 완료된 계획 기록
- `tech-debt-tracker.md`: 우선순위 기반 기술 부채 목록

## 파일명 규칙 (충돌 방지)
- 형식: `YYYY-MM-DD-HHMMSS-topic-owner.md`
- 예시: `2026-04-24-154210-internal-speech-api-chan.md`
- 초(`HHMMSS`)까지 포함해 같은 날/같은 주제라도 파일명이 겹치지 않게 합니다.

## 협업 규칙 (4인 이상 기준)
- `active/`는 각자 자기 파일만 수정합니다.
- `completed/` 이동은 문서 담당 1명이 일괄 처리합니다.
- `completed/` 파일은 완료 후 읽기 전용으로 간주하고 수정하지 않습니다.
- `completed/`는 월 단위 폴더(`completed/YYYY-MM/`)로 저장합니다.
- 공용 상태 업데이트는 `tech-debt-tracker.md`에 append 방식으로 남깁니다.

## 빠른 생성 명령
- `./scripts/new-exec-plan.sh <topic> <owner>`

## 권장 섹션
- 배경
- 목표
- 범위/비범위
- 단계
- 위험/롤백
- 검증
- 결정 로그
