#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env.prod}"
UPSTREAM_FILE="${UPSTREAM_FILE:-deploy/nginx/conf.d/upstream.conf}"
DRAIN_SECONDS="${DRAIN_SECONDS:-10}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-2}"

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" --profile green)

log() {
    printf '[deploy] %s\n' "$*"
}

fail() {
    printf '[deploy][error] %s\n' "$*" >&2
    exit 1
}

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
        app-blue) printf 'app-green\n' ;;
        app-green) printf 'app-blue\n' ;;
        *) fail "알 수 없는 app 슬롯입니다: $1" ;;
    esac
}

is_running() {
    local service="$1"
    local id
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

wait_for_service_health() {
    local service="$1"
    local body

    log "$service health check 대기 중"
    for _ in $(seq 1 "$HEALTH_RETRIES"); do
        body="$("${COMPOSE[@]}" run --rm --no-deps --entrypoint wget nginx -q -O - "http://${service}:8080/actuator/health" 2>/dev/null || true)"
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

    log "nginx 경유 health check 대기 중"
    for _ in $(seq 1 "$HEALTH_RETRIES"); do
        body="$("${COMPOSE[@]}" exec -T nginx wget -q -O - "http://127.0.0.1/actuator/health" 2>/dev/null || true)"
        if printf '%s' "$body" | grep -q '"status":"UP"'; then
            log "nginx 경유 health check 성공"
            return
        fi
        sleep "$HEALTH_INTERVAL_SECONDS"
    done

    "${COMPOSE[@]}" logs --tail=120 nginx || true
    fail "nginx 경유 health check 실패"
}

reload_nginx() {
    "${COMPOSE[@]}" up -d nginx
    "${COMPOSE[@]}" exec -T nginx nginx -t
    "${COMPOSE[@]}" exec -T nginx nginx -s reload
}

switch_nginx_upstream() {
    local next_service="$1"
    local backup_file

    backup_file="$(mktemp)"
    if [[ -f "$UPSTREAM_FILE" ]]; then
        cp "$UPSTREAM_FILE" "$backup_file"
    else
        write_upstream "$active"
        cp "$UPSTREAM_FILE" "$backup_file"
    fi

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
    log "discord-bot 단일 프로세스 재시작"
    "${COMPOSE[@]}" up -d --no-deps --force-recreate discord-bot
}

require_env_file
mkdir -p "$(dirname "$UPSTREAM_FILE")"

active="$(active_service)"
inactive="$(opposite_service "$active")"

log "현재 active 슬롯: $active"
log "배포 대상 inactive 슬롯: $inactive"

log "db/redis 실행 보장"
"${COMPOSE[@]}" up -d db redis

if ! is_running "$active"; then
    log "active 슬롯이 실행 중이 아니므로 $active 초기 배포를 수행"
    "${COMPOSE[@]}" up -d --build "$active"
    wait_for_service_health "$active"
    switch_nginx_upstream "$active"
    wait_for_nginx_health
    restart_bot
    log "초기 배포 완료"
    exit 0
fi

log "$inactive 새 버전 빌드 및 실행"
"${COMPOSE[@]}" up -d --build "$inactive"
wait_for_service_health "$inactive"

log "nginx upstream 전환: $active -> $inactive"
switch_nginx_upstream "$inactive"
wait_for_nginx_health

log "${DRAIN_SECONDS}s 대기 후 이전 슬롯 중지: $active"
sleep "$DRAIN_SECONDS"
"${COMPOSE[@]}" stop "$active" || true

restart_bot
log "무중단 배포 완료: active=$inactive"
