---
generated-by: ai-draft
reviewed-by: madupal
reviewed-at: 2026-04-26
evidence: PR #5, bash ./scripts/check-agent-docs.sh, bash ./scripts/doc-gardening.sh, ./gradlew spotlessCheck
---

# 구현 기록 규약 하네스 보강

> AI가 만든 구현 기록을 작성자별로 보관하고, 검토 상태와 근거를 드러내도록 하네스 문서 규칙과 자동 검사를 보강했습니다.

- 구현 일자: 2026-04-25
- 작성자: madupal
- 담당 파트: 하네스 문서 체계
- 관련 테이블: 없음

AI가 초안을 작성할 때는 `reviewed-by`, `reviewed-at`, `evidence`를 비워둡니다.
개발자가 사실관계와 근거를 검토한 뒤 직접 채웁니다.

---

## 왜 이렇게 구현했나

### 문제 상황
구현 중 사용한 기술 선택, 대안, 문제 해결 과정을 기록하고 싶었지만, 모든 기록을 한 폴더에 쌓으면 여러 작성자의 문서가 섞이고 충돌 가능성이 커지는 문제가 있었습니다.

또한 AI가 구현 기록을 작성할 경우 실제로 검토하지 않은 대안, 측정하지 않은 수치, 근거 없는 포트폴리오 문구가 공용 지식처럼 남을 위험이 있었습니다.

### 선택한 방식
`docs/impl/{author-kebab-case}/{MMDD}-{feature-kebab-case}.md` 구조를 도입하고, 공용 템플릿은 `docs/references/impl-record-template.md`에 두었습니다.

AI 초안과 개발자 검토 상태를 구분하기 위해 구현 기록 문서에 YAML frontmatter를 사용했습니다.
`check-agent-docs.sh`는 필수 문서, 구현 기록 형식, 병합 전 검토 필드 값을 검사하고, `doc-gardening.sh`는 미검토 AI 초안과 `evidence` 누락 상태를 리포트하도록 보강했습니다.

### 고려했던 다른 방법들
| 방식 | 장점 | 단점 | 출처/근거 | 선택 여부 |
|------|------|------|-----------|----------|
| `docs/impl/` 단일 폴더에 전체 기록 저장 | 구조가 단순함 | 4인 이상 병렬 작업 시 파일이 섞이고 소유권이 흐려짐 | 사용자 요청: 작성자별 분리 필요 | 미선택 |
| 작성자별 `docs/impl/{author}/` 폴더 저장 | 충돌이 줄고 작성자 소유권이 명확함 | 폴더 규칙과 검사 로직이 추가됨 | 사용자 요청: `madupal` 같은 작성자별 폴더 운영 | 선택 |
| 템플릿만 추가하고 검사는 생략 | 도입 비용이 낮음 | 규칙이 지켜지지 않아도 감지하기 어려움 | 리뷰 피드백: frontmatter 검사가 필요 | 미선택 |
| 템플릿 + 스크립트 검사 + 가드닝 리포트 | 형식과 미검토 상태를 자동으로 드러낼 수 있음 | 스크립트 유지보수 비용이 생김 | 하네스 엔지니어링 원칙: 문서 규칙을 자동 검사로 흡수 | 선택 |
| 병합 전 검토 필드 필수화 | 검토되지 않은 AI 초안이 dev/main에 들어가는 것을 막을 수 있음 | PR 병합 전 개발자가 근거 필드를 채워야 함 | CI 트리거 확인 결과 main/dev PR에서 `check-agent-docs.sh` 실행 | 선택 |

### 이 방식을 선택한 이유
작성자별 폴더 구조는 팀원이 동시에 기록을 남겨도 충돌을 줄이고, GitHub ID 기반으로 소유권을 명확히 만들 수 있습니다.

YAML frontmatter는 사람이 읽기에도 충분히 단순하면서 스크립트로 검사하기 쉽습니다. 그래서 `generated-by`, `reviewed-by`, `reviewed-at`, `evidence` 필드를 표준화해 AI 초안이 검토 완료 문서처럼 보이지 않도록 했습니다.

CI가 main/dev 대상 PR에서 `check-agent-docs.sh`를 실행하므로, 구현 기록이 병합되려면 `reviewed-by`, `reviewed-at`, `evidence`가 채워진 검토 완료 상태여야 합니다.

---

## 핵심 구현 내용

### 구조 설명
- `docs/impl/README.md`: 구현 기록의 작성 대상, 생략 가능 기준, 작성자별 경로 규칙, AI 초안 검토 워크플로우를 설명합니다.
- `docs/references/impl-record-template.md`: 실제 구현 기록 템플릿과 AI 초안 검토 규칙을 제공합니다.
- `AGENTS.md`: Claude, GPT, Codex가 공통으로 읽는 진입점에 구현 기록 규칙과 AI 초안 주의사항을 추가했습니다.
- `.github/pull_request_template.md`: PR 작성 시 구현 기록 필요 여부와 AI 생성 기록 검토 여부를 확인하도록 했습니다.
- `scripts/check-agent-docs.sh`: 구현 기록 파일명, 작성자 폴더명, frontmatter 필드 존재 여부와 검토 필드 값을 검사합니다.
- `scripts/doc-gardening.sh`: 구현 기록 현황, 규칙 위반, 미검토 AI 초안, `evidence` 누락 상태를 리포트합니다.

### 핵심 코드
`check-agent-docs.sh`는 구현 기록 파일마다 frontmatter 필드와 병합 전 필수 검토 값을 검사합니다.

```bash
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
    exit 1
  fi
done
```

`doc-gardening.sh`는 미검토 AI 초안을 리포트합니다.

```bash
generated_by="$(grep -m 1 "^generated-by:" "$file" | sed 's/^generated-by:[[:space:]]*//')"
reviewed_by="$(grep -m 1 "^reviewed-by:" "$file" | sed 's/^reviewed-by:[[:space:]]*//')"

if [[ "$generated_by" == "ai-draft" && -z "$reviewed_by" ]]; then
  unreviewed_impl_entries+=("- \`$file\`")
fi
```

### 설계 결정사항
- 작성자별 폴더 사용: 병렬 작업 시 문서 충돌을 줄이고 작성자 소유권을 명확히 하기 위해 선택했습니다.
- YAML frontmatter 사용: 나중에 스크립트로 검토 상태와 근거 누락을 찾기 쉽게 하기 위해 선택했습니다.
- `check-agent-docs.sh`는 검토 필드 값을 검사: dev/main에 검토되지 않은 AI 초안이 병합되지 않도록 하기 위해 선택했습니다.
- `doc-gardening.sh`는 미검토 상태를 리포트: 로컬/피처 브랜치에서 생긴 초안이 방치되는지 주기적으로 드러내기 위해 선택했습니다.

---

## 어려웠던 점 & 해결 방법

### 문제 1: AI 초안이 검토 완료 문서처럼 보일 위험
- 상황: `docs/impl/madupal/` 같은 경로는 개발자가 작성한 확정 기록처럼 보일 수 있습니다.
- 시도한 것: 구현 기록 템플릿에 `generated-by`, `reviewed-by`, `reviewed-at`, `evidence` frontmatter를 추가했습니다.
- 해결: AI는 검토 필드를 비워두고, 개발자가 검토 후 직접 채우는 규칙을 `AGENTS.md`, 템플릿, README에 반복해서 연결했습니다.

### 문제 2: 그럴듯한 수치와 대안 비교가 만들어질 위험
- 상황: 포트폴리오 메모, 실험 결과, 대안 비교는 AI가 근거 없이 채우기 쉬운 영역입니다.
- 시도한 것: 수치 작성 금지 규칙, 대안 표의 `출처/근거` 컬럼, 실험 데이터가 없으면 실험 결과 섹션을 삭제하는 규칙을 추가했습니다.
- 해결: 근거 없는 수치와 대안이 문서에 남지 않도록 템플릿 자체에 작성 제약을 넣었습니다.

### 문제 3: 규칙이 문서에만 있고 자동으로 드러나지 않을 위험
- 상황: 템플릿에만 규칙을 두면 지켜지지 않아도 알기 어렵습니다.
- 시도한 것: `check-agent-docs.sh`에 frontmatter 필드와 검토 필드 값 검사를 추가하고, `doc-gardening.sh`에 미검토 AI 초안과 `evidence` 누락 리포트를 추가했습니다.
- 해결: CI와 주간 가드닝에서 구현 기록 규칙 위반과 미검토 상태를 볼 수 있게 했고, dev/main 병합 전에는 검토 완료 상태만 통과하도록 했습니다.

---

## 다음에 개선할 점

- [ ] `doc-gardening` 리포트에서 작성자별 오래된 미검토 초안 기준 추가
- [ ] 실제 구현 기록이 쌓인 뒤 템플릿 항목이 과하거나 부족한지 점검

---

## 포트폴리오 메모

> AI가 생성한 구현 기록이 검토되지 않은 채 팀 지식처럼 누적될 수 있는 문제를 발견했다.
> 작성자별 기록 구조와 frontmatter 기반 검토 상태를 도입하고, 문서 검사와 가드닝 리포트로 미검토 초안을 드러내도록 설계했다.
> 그 결과 구현 기록의 위치, 소유자, 검토 상태, 근거 유무를 PR 병합 기준과 주간 문서 관리 흐름에서 확인할 수 있게 됐다.
