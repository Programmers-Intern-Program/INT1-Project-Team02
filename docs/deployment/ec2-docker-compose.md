# EC2 Docker Compose Deployment

제공받은 EC2 개발 서버에서 Docker Compose로 API, Discord bot, DB, Redis를 함께 실행한다.

## 제공 환경

- EC2 instance
- EBS 50GB
- S3 bucket

## 실행 구성

- Nginx: 외부 `8000` -> active API 슬롯
- Spring Boot API: `app-blue` / `app-green` blue-green 슬롯
- Discord bot: 단일 Java main class 실행
- PostgreSQL with pgvector: EC2 localhost에만 바인딩
- Redis: EC2 localhost에만 바인딩

Discord bot은 같은 토큰으로 두 프로세스를 동시에 띄우면 interaction 중복 처리가 발생할 수 있으므로 blue-green 대상에서 제외한다.

## First Server Setup

```bash
sudo apt-get update
sudo apt-get install -y git docker.io docker-compose-plugin awscli
sudo usermod -aG docker ubuntu
newgrp docker
```

## First Deploy

```bash
git clone https://github.com/Programmers-Intern-Program/INT1-Project-Team02.git
cd INT1-Project-Team02
git checkout dev

vi .env.prod

./scripts/deploy-blue-green.sh
```

`.env.prod`는 서버에서 직접 만들고 Git에 올리지 않는다. 최소한 아래 값들은 실제 값으로 넣어야 한다.

```properties
APP_PORT=8000
DB_NAME=flodiback
DB_USERNAME=flodiback
DB_PASSWORD=
DB_PORT=5432
REDIS_PORT=6379
INTERNAL_API_KEY=
DISCORD_TOKEN=
DISCORD_BOT_PREFIX=!
DISCORD_ALLOWED_GUILD_IDS=
DISCORD_DEFAULT_MEETING_ID=1
OPENAI_API_KEY=
OPENAI_EMBEDDING_API_KEY=
GLM_ENABLED=false
GLM_API_KEY=
GLM_MODEL=glm-5.1
GLM_API_URL=https://api.z.ai/api
GLM_TIMEOUT_MS=30000
GLM_MAX_RETRIES=1
```

The API is published on port `8000` by default.

```bash
curl http://localhost:8000/actuator/health
```

## Zero-Downtime API Deploy

업데이트 배포는 `docker compose up -d --build`를 직접 실행하지 않고 blue-green 스크립트로 수행한다.

```bash
git pull
./scripts/deploy-blue-green.sh
```

스크립트 동작:

1. 현재 Nginx upstream의 active 슬롯을 확인한다.
2. inactive 슬롯에 새 API 컨테이너를 빌드하고 실행한다.
3. inactive 슬롯의 `/actuator/health`가 `UP`인지 확인한다.
4. Nginx upstream을 inactive 슬롯으로 전환하고 reload한다.
5. Nginx 경유 `/actuator/health`가 `UP`인지 확인한다.
6. 짧은 대기 후 이전 active 슬롯을 중지한다.
7. Discord bot은 마지막에 단일 프로세스로 재시작한다.

`deploy/nginx/conf.d/upstream.conf`는 스크립트가 생성하는 런타임 파일이며 Git에 올리지 않는다. 기본 형식은 `deploy/nginx/conf.d/upstream.conf.example`을 참고한다.

기본 대기값은 환경변수로 조정할 수 있다.

```bash
DRAIN_SECONDS=15 HEALTH_RETRIES=40 ./scripts/deploy-blue-green.sh
```

## Logs

```bash
docker compose -f docker-compose.prod.yml logs -f nginx
docker compose -f docker-compose.prod.yml logs -f app-blue
docker compose -f docker-compose.prod.yml logs -f app-green
docker compose -f docker-compose.prod.yml logs -f discord-bot
```

## State Check

```bash
docker compose -f docker-compose.prod.yml ps
cat deploy/nginx/conf.d/upstream.conf
curl http://localhost:8000/actuator/health
```

## S3

The provided EC2 instance should use its IAM role for S3 access. Do not put AWS access keys in `.env.prod`.

```bash
aws s3 ls
aws s3 ls s3://YOUR_BUCKET_NAME
```
