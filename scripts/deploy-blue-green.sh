#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# phase 인수: build | switch | cleanup | all(기본값)
# GitHub Actions에서는 각 job이 해당 phase만 실행한다.
# 수동 실행 시 인수 없이 호출하면 all로 전체를 순서대로 수행한다.
PHASE="${1:-all}"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env.prod}"
UPSTREAM_FILE="${UPSTREAM_FILE:-deploy/nginx/conf.d/upstream.conf}"
DRAIN_SECONDS="${DRAIN_SECONDS:-10}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-2}"
# build → switch → cleanup 단계 간 슬롯 상태를 전달하는 임시 파일
DEPLOY_STATE_FILE="${DEPLOY_STATE_FILE:-/tmp/flodi-deploy.state}"

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" --profile green)

log() { printf '[deploy] %s\n' "$*"; }
fail() { printf '[deploy][error] %s\n' "$*" >&2; exit 1; }

case "$PHASE" in
    all|build|switch|cleanup) ;;
    *) fail "알 수 없는 phase: $PHASE (all|build|switch|cleanup)" ;;
esac

# ── 공통 함수 ─────────────────────────────────────────────────────────────────

require_env_file() {
    [[ -f "$ENV_FILE" ]] || fail "$ENV_FILE 파일이 없습니다. EC2 서버에서 먼저 생성하세요."
}

active_service() {
    if [[ -f "$UPSTREAM_FILE" ]] && grep -qE 'app-(blue|green)' "$UPSTREAM_FILE"; then
        grep -Eo 'app-(blue|green)' "$UPSTREAM_FILE" | head -n 1
        return
    fi
    printf 'app-blue\n'
}

opposite_service() {
    case "$1" in
        app-blue)  printf 'app-green\n' ;;
        app-green) printf 'app-blue\n' ;;
        *) fail "알 수 없는 app 슬롯: $1" ;;
    esac
}

is_running() {
    local service="$1" id
    id="$("${COMPOSE[@]}" ps -q "$service" 2>/dev/null || true)"
    [[ -n "$id" ]] || return 1
    [[ "$(docker inspect -f '{{.State.Running}}' "$id" 2>/dev/null || true)" == "true" ]]
}

write_upstream() {
    local service="$1"
    cat > "$UPSTREAM_FILE" <<EOF
upstream flodi_backend {
    server ${service}:8080;
}
EOF
}

ensure_upstream_file() {
    mkdir -p "$(dirname "$UPSTREAM_FILE")"
    # upstream.conf가 없으면 nginx가 시작할 수 없으므로 기본값(app-blue)으로 생성한다.
    if [[ ! -f "$UPSTREAM_FILE" ]]; then
        log "upstream.conf 없음 → app-blue 기본값으로 생성"
        write_upstream "app-blue"
    fi
}

wait_for_service_health() {
    local service="$1" body
    log "$service health check 대기 중"
    for _ in $(seq 1 "$HEALTH_RETRIES"); do
        body="$("${COMPOSE[@]}" run --rm --no-deps --entrypoint wget nginx \
            -q -O - "http://${service}:8080/actuator/health" 2>/dev/null || true)"
        if printf '%s' "$body" | grep -q '"status":"UP"'; then
            log "$service health check 성공"
            return
        fi
        sleep "$HEALTH_INTERVAL_SECONDS"
    done
    "${COMPOSE[@]}" logs --tail=120 "$service" || true
    fail "$service health check 실패"
}

wait_for_nginx_health() {
    local body
    log "nginx health check 대기 중"
    for _ in $(seq 1 "$HEALTH_RETRIES"); do
        # Alpine은 localhost를 IPv6(::1)로 resolve하므로 127.0.0.1을 명시한다.
        body="$("${COMPOSE[@]}" exec -T nginx wget -q -O - \
            "http://127.0.0.1/nginx-health" 2>/dev/null || true)"
        if printf '%s' "$body" | grep -q 'ok'; then
            log "nginx health check 성공"
            return
        fi
        sleep "$HEALTH_INTERVAL_SECONDS"
    done
    "${COMPOSE[@]}" logs --tail=120 nginx || true
    fail "nginx health check 실패"
}

reload_nginx() {
    "${COMPOSE[@]}" up -d nginx
    "${COMPOSE[@]}" exec -T nginx nginx -t
    "${COMPOSE[@]}" exec -T nginx nginx -s reload
}

switch_nginx_upstream() {
    local next_service="$1" backup_file
    backup_file="$(mktemp)"
    cp "$UPSTREAM_FILE" "$backup_file"
    write_upstream "$next_service"

    if ! reload_nginx; then
        log "nginx 전환 실패. 이전 upstream으로 복구합니다."
        cp "$backup_file" "$UPSTREAM_FILE"
        reload_nginx || true
        rm -f "$backup_file"
        fail "nginx upstream 전환 실패"
    fi
    rm -f "$backup_file"
}

restart_bot() {
    log "discord-bot 재시작"
    "${COMPOSE[@]}" up -d --no-deps --force-recreate discord-bot
}

# ── Phase: build ──────────────────────────────────────────────────────────────
# - db/redis 기동 보장
# - inactive 슬롯 빌드 및 실행
# - 앱 헬스체크
# - 상태 파일 기록 (switch/cleanup 단계에서 사용)
run_build() {
    require_env_file
    ensure_upstream_file

    local active inactive
    active="$(active_service)"
    inactive="$(opposite_service "$active")"

    log "현재 active 슬롯: $active"
    log "배포 대상 inactive 슬롯: $inactive"

    log "db/redis 실행 보장"
    "${COMPOSE[@]}" up -d db redis

    if ! is_running "$active"; then
        log "active 슬롯 미실행 → $active 초기 배포 수행"
        "${COMPOSE[@]}" up -d --build "$active"
        wait_for_service_health "$active"
        switch_nginx_upstream "$active"
        wait_for_nginx_health
        restart_bot
        printf 'initial_deploy=true\n' > "$DEPLOY_STATE_FILE"
        log "초기 배포 완료"
        return
    fi

    log "$inactive 빌드 및 실행"
    "${COMPOSE[@]}" up -d --build "$inactive"
    wait_for_service_health "$inactive"

    printf 'active=%s\ninactive=%s\ninitial_deploy=false\n' \
        "$active" "$inactive" > "$DEPLOY_STATE_FILE"
    log "빌드 완료: $inactive 슬롯 준비됨"
}

# ── Phase: switch ─────────────────────────────────────────────────────────────
# - nginx upstream을 inactive 슬롯으로 전환
# - nginx 헬스체크로 트래픽 전환 확인
run_switch() {
    [[ -f "$DEPLOY_STATE_FILE" ]] \
        || fail "상태 파일이 없습니다. build 단계를 먼저 실행하세요."
    # shellcheck source=/dev/null
    source "$DEPLOY_STATE_FILE"

    if [[ "${initial_deploy:-false}" == "true" ]]; then
        log "초기 배포 완료됨 → switch 단계 건너뜀"
        return
    fi

    log "nginx upstream 전환: $active → $inactive"
    switch_nginx_upstream "$inactive"
    wait_for_nginx_health
    log "트래픽 전환 완료"
}

# ── Phase: cleanup ────────────────────────────────────────────────────────────
# - drain 대기 후 이전 슬롯 종료
# - discord-bot 재시작 (새 이미지 반영)
run_cleanup() {
    [[ -f "$DEPLOY_STATE_FILE" ]] \
        || fail "상태 파일이 없습니다. build/switch 단계를 먼저 실행하세요."
    # shellcheck source=/dev/null
    source "$DEPLOY_STATE_FILE"

    if [[ "${initial_deploy:-false}" == "true" ]]; then
        log "초기 배포 완료됨 → cleanup 단계 건너뜀"
        rm -f "$DEPLOY_STATE_FILE"
        return
    fi

    log "${DRAIN_SECONDS}s 대기 후 이전 슬롯 중지: $active"
    sleep "$DRAIN_SECONDS"
    "${COMPOSE[@]}" stop "$active" || true

    restart_bot
    rm -f "$DEPLOY_STATE_FILE"
    log "무중단 배포 완료: active=$inactive"
}

# ── 실행 ──────────────────────────────────────────────────────────────────────
case "$PHASE" in
    build)   run_build ;;
    switch)  run_switch ;;
    cleanup) run_cleanup ;;
    all)     run_build; run_switch; run_cleanup ;;
esac
