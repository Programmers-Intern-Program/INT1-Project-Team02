# 아키텍처

## 개요
`flodi-back`는 Discord 기반 회의 도우미를 위한 Spring Boot 백엔드입니다.
현재는 도메인 모델링과 공통 응답/예외 구조를 중심으로 구성되어 있습니다.

## 패키지 지도
- `com.flodiback`: 애플리케이션 진입점
- `com.flodiback.api`: HTTP/Public/Internal API 엔트리포인트와 API DTO
- `com.flodiback.application`: 유스케이스 서비스, command/result 모델, 트랜잭션 경계
- `com.flodiback.domain`: 엔티티, 도메인 타입, repository port, 도메인 규칙
- `com.flodiback.infrastructure`: Spring Data JPA, 외부 연동, 기술 구현 adapter
- `com.flodiback.global`: 공통 횡단 관심사 (응답 포맷, 예외 처리, 공통 설정)

## 계층 의존성
- `api`는 `application`, `domain`, `global`을 사용할 수 있습니다.
- `application`은 `domain`을 조율하며 HTTP 계약, `RsData`, infrastructure 구현에 의존하지 않습니다.
- `domain`은 API, application, infrastructure, HTTP 응답 포맷을 모르는 비즈니스 중심 계층으로 유지합니다.
- `infrastructure`는 domain/application port를 구현하는 기술 세부사항을 담습니다.
- `global`은 프로젝트 세부 계층에 의존하지 않는 공통 기반으로 유지합니다.

## 도메인 경계
- `server`: Discord 서버 정보와 API 키 소유 관계
- `project`: 프로젝트 메타데이터와 작업 로그
- `meeting`: 회의 라이프사이클과 컨텍스트 캐시
- `meetinglog`: 발화(utterance)와 회의 요약
- `decision`: 프로젝트 결정사항과 임베딩

## 데이터 모델 핵심 관계
- `Project`는 선택적으로 `DiscordServer`에 연결됩니다.
- `Meeting`은 반드시 `Project`에 연결됩니다.
- `Utterance`는 반드시 `Meeting`에 연결됩니다.
- `MeetingSummary`는 `Meeting`과 1:1 관계입니다.
- `WorkLog`는 `Meeting`과 `Project`에 연결됩니다.
- `Decision`은 `Project`에 필수, `Meeting`에는 선택적으로 연결됩니다.
- `ApiKey`는 반드시 `DiscordServer`에 연결됩니다.
- `ContextCache`는 반드시 `Meeting`에 연결됩니다.

## 공통 규칙
- Public/Internal API는 `docs/references/`에 명시적 계약으로 기록합니다.
- 오류 응답은 `GlobalExceptionHandler`를 통해 `RsData` 형태로 일관되게 반환합니다.
- `RsData`와 `ResponseEntity`는 API/공통 예외 처리 계층에서만 사용하고 domain/application 계층으로 흘려보내지 않습니다.
- Spring Data JPA repository 구현은 `infrastructure.persistence`에 두고, application은 domain repository port에 의존합니다.
- 도메인/계약의 비호환 변경은 `docs/exec-plans/`에 계획과 이행 기록을 남깁니다.

## 에이전트 작업 불변 조건
- 경계와 계약은 항상 명시적이고 버전 관리 가능한 형태로 유지합니다.
- 로컬에서 재현 가능한 검증 절차를 우선합니다.
- 엔티티/API/계층 경계가 바뀌면 아키텍처 문서도 함께 갱신합니다.
