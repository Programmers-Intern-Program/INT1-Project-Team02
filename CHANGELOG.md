# Changelog

## [0.2.0](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/compare/v0.1.0...v0.2.0) (2026-05-14)


### ✨ 새 기능

* dev 머지 시 자동 태깅 및 main 머지 시 EC2 자동 배포 워크플로 추가 ([7ca3c82](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/7ca3c82d91d2ec2fd263ff1a338ac7444fa195a8))
* GET /api/v1/projects/{id}/work-logs API 추가 closes [#118](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/issues/118) ([12f9357](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/12f935720cfe3d841c669520fe350c726274baf5))
* NPM 리버스프록시 도입으로 HTTPS 인증서 자동 관리 closes [#95](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/issues/95) ([8b7bbf4](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/8b7bbf49e45ec55ae04c3bb40612b9ccff68f7f5))
* ProjectResponse에 channelName 필드 추가 closes [#111](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/issues/111) ([5c8c4b4](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/5c8c4b454bcfe96649de688fa06193dd37572da1))
* ProjectResponse에 serverName 필드 추가 closes [#109](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/issues/109) ([865f5e3](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/865f5e393862374abe184510c6f0b1ef1af3b02a))
* release-please 자동 버전 관리 추가, 수동 태깅 워크플로 제거 ([363881f](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/363881f6cf7fe349caa8376be47e4d041a8debed))
* rolling summary progress WebSocket push 추가 ([17ada9a](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/17ada9a75c5f0cc93a117a886f09e5e206456ac1))
* 도메인 연결 및 HTTPS 설정 (api.flodi.site) closes [#88](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/issues/88) ([2592d1e](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/2592d1e4c54ac00d3db62bc6b679871efc3acefa))
* 웹 대시보드 인증 연동 및 프로젝트-서버 연결 수정 ([90476c0](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/90476c083fa92930caf4078a1ddc9409ce7c7667))
* 작업 로그 CANCELLED 상태 추가 (취소 발언 감지) ([ec4c439](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/ec4c4396b88af785407971a109aa49ec08a6fde1))
* 프로젝트 생성 시 WebSocket 이벤트 발행 closes [#114](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/issues/114) ([1d3a93d](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/1d3a93d32196ba8a233cf2a5fe6eba1c671d90f2))
* 회의 분석 시 기존 작업 로그 상태 자동 업데이트 closes [#123](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/issues/123) ([e6bd3a6](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/e6bd3a600d76dc72f49a5f8d8af86164e35675c4))


### 🐛 버그 수정

* [type]:desc 공백 없는 경우 정규화 패턴 추가 ([dd97a71](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/dd97a71485226f5ae5b3706661b9af980eca284d))
* nginx 헬스체크 localhost → 127.0.0.1 수정 ([7a5a17e](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/7a5a17e538f9d06196f305d52981a44042cb354b))
* nginx 헬스체크에서 localhost → 127.0.0.1로 수정 ([869b529](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/869b52992f783ee79e8aa3950e05e95aa3f39716))
* NPM letsencrypt 볼륨 누락 추가 ([0aadabb](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/0aadabbc587f96e38bee25da8c98d30ee99c5407))
* OpenAI Realtime PCM 버퍼 상한 추가 ([5d44495](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/5d44495a556750109e3a88eae7fe68dfdc0d9fac))
* OpenAI Realtime rate limit 쿨다운 처리 ([2631765](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/2631765744977b9791d4f4a2f19dce40b975eb9b))
* OpenAI Realtime 오디오 append 배치 전송 적용 ([fdb509e](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/fdb509e45fa5c452a78ce530f4beeafaf6df5db3))
* PR 정규화를 git log --merges 대신 GitHub API로 교체 ([4560c3e](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/4560c3e8c94808c2f9574bc908c68281214d467d))
* ProjectServiceTest에 ApplicationEventPublisher mock 추가 ([15d67c8](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/15d67c808c266601c6ad972f6fbff42f71d28450))
* release-please manifest 패키지 설정 수정 ([4e5aafa](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/4e5aafa1b9cbd492bce2f61ed10febfe4e29e994))
* release-please PAT 토큰 사용 ([a054de5](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/a054de5e647f31775269efa58a7e7b4f9396477b))
* release-please PR 제목 정규화 전처리 추가 closes [#104](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/issues/104) ([d07fee3](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/d07fee3c303dfe15b19d9eb3bac4f72bcf0a5f33))
* release-please 수동 실행 트리거 추가 (workflow_dispatch) ([58161fc](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/58161fc11efe1881d1ae7aeedf92b95635aad7f2))
* release-please 전처리 토큰 분리 ([1229eb4](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/1229eb44b9e0dee0141847a350d4bf93ed79c0a0))
* RequestParam import 추가 및 테스트 connectChannel 시그니처 수정 ([6f61225](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/6f612255ecb867b6372ba529cbeeb3053cca12a8))
* spotless 포맷 적용 ([3507891](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/35078919f74a5be5aa3a46ba255f2c171824fe89))
* 릴리즈 기준 배포 플로우 변경 ([cef0d44](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/cef0d44a274cdd731c4a1916ac325bc811d58e15))
* 빈 guildIds일 때 서버 소속 프로젝트 접근 차단 ([231250e](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/231250e883f5392ab5437e957ece4455e09c0568))
* 서버 시간대 UTC 고정 ([29276d8](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/29276d838909617537592c778fb27d8a2c5ba7ec))
* 여러 Discord 서버 소속 시 회의 목록 403 오류 수정 closes [#116](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/issues/116) ([bdfbe15](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/bdfbe15390c1ce1d0a4a5ffd69a3ffde274e3bdf))
* 작업 로그 상태 업데이트 CI 실패 수정 ([5d2a0cd](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/5d2a0cde689dd953cf674271a5bcf4123e7407b7))


### ♻️ 리팩토링

* deploy 스크립트 phase 분리 및 CI job 세분화 ([6d6a291](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/6d6a291c439ea1b0770b0678b8a36551d47db4dc))
* DISCORD_DEFAULT_MEETING_ID 환경변수 및 관련 dead code 제거 ([9cc537f](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/9cc537f4a5fb83c3f9b4f2681cd9cc3623c6eb4d))
* 테스트 환경 설정을 application-test.yml로 통합 ([496fbfb](https://github.com/Programmers-Intern-Program/INT1-Project-Team02/commit/496fbfbb036dbf35abf4ec9a7e37decf0b49431d))
