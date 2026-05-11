# EC2 Docker Compose Deployment

제공받은 EC2 개발 서버에서 Docker Compose로 API, Discord bot, DB, Redis를 함께 실행한다.

## 제공 환경

- EC2 instance
- EBS 50GB
- S3 bucket

## 실행 구성

- Spring Boot API: 외부 `8000` -> 컨테이너 `8080`
- Discord bot: 별도 Java main class 실행
- PostgreSQL with pgvector: EC2 localhost에만 바인딩
- Redis: EC2 localhost에만 바인딩

## First Server Setup

```bash
sudo apt-get update
sudo apt-get install -y git docker.io docker-compose-plugin
sudo usermod -aG docker ubuntu
newgrp docker
```

## Deploy

```bash
git clone https://github.com/Programmers-Intern-Program/INT1-Project-Team02.git
cd INT1-Project-Team02

vi .env.prod

docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
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

## Update Deployment

```bash
git pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

## Logs

```bash
docker compose -f docker-compose.prod.yml logs -f app
docker compose -f docker-compose.prod.yml logs -f discord-bot
```

## S3

The provided EC2 instance should use its IAM role for S3 access. Do not put AWS access keys in `.env.prod`.

```bash
aws s3 ls
aws s3 ls s3://YOUR_BUCKET_NAME
```
