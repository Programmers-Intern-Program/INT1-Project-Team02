# DB 스키마 스냅샷 (수동)

기준 원본은 `src/main/java/com/flodiback/domain/**/entity` 하위 JPA 엔티티입니다.

## 테이블

### `discord_servers`
- `id` BIGINT PK
- `guild_id` VARCHAR(50) UNIQUE NOT NULL
- `guild_name` VARCHAR(100) NOT NULL
- `created_at` TIMESTAMP NOT NULL
- `deleted_at` TIMESTAMP NULL

### `api_keys`
- `id` BIGINT PK
- `server_id` BIGINT FK -> `discord_servers.id` NOT NULL
- `key_hash` VARCHAR(255) NOT NULL
- `description` VARCHAR(100) NULL
- `created_at` TIMESTAMP NOT NULL

### `projects`
- `id` BIGINT PK
- `server_id` BIGINT FK -> `discord_servers.id` NULL
- `name` VARCHAR(100) NOT NULL
- `description` TEXT NULL
- `tech_stack` VARCHAR(255) NULL
- `created_at` TIMESTAMP NOT NULL

### `meetings`
- `id` BIGINT PK
- `project_id` BIGINT FK -> `projects.id` NOT NULL
- `title` VARCHAR(200) NULL
- `started_at` TIMESTAMP NOT NULL
- `ended_at` TIMESTAMP NULL
- `status` VARCHAR(20) NOT NULL

### `utterances`
- `id` BIGINT PK
- `meeting_id` BIGINT FK -> `meetings.id` NOT NULL
- `speaker_name` VARCHAR(100) NOT NULL
- `speaker_discord_id` VARCHAR(50) NOT NULL
- `content` TEXT NOT NULL
- `spoken_at` TIMESTAMP NOT NULL

### `meeting_summaries`
- `id` BIGINT PK
- `meeting_id` BIGINT FK -> `meetings.id` UNIQUE NOT NULL
- `summary` TEXT NOT NULL
- `unresolved_items` TEXT NULL
- `is_confirmed` BOOLEAN NOT NULL
- `created_at` TIMESTAMP NOT NULL

### `context_cache`
- `id` BIGINT PK
- `meeting_id` BIGINT FK -> `meetings.id` NOT NULL
- `compressed_text` TEXT NOT NULL
- `token_count` INT NOT NULL
- `turn_range` VARCHAR(50) NOT NULL
- `created_at` TIMESTAMP NOT NULL

### `decisions`
- `id` BIGINT PK
- `project_id` BIGINT FK -> `projects.id` NOT NULL
- `meeting_id` BIGINT FK -> `meetings.id` NULL (ON DELETE SET NULL)
- `content` TEXT NOT NULL
- `embedding` VECTOR(1536) NULL — OpenAI text-embedding-3-small
- `content_tsv` TSVECTOR NULL — tsvector 트리거 자동 갱신 (하이브리드 서치용)
- `decided_at` TIMESTAMP NOT NULL
- INDEX: `idx_decisions_embedding` HNSW (m=16, ef_construction=64)
- INDEX: `idx_decisions_tsv` GIN

### `work_logs`
- `id` BIGINT PK
- `meeting_id` BIGINT FK -> `meetings.id` NOT NULL
- `project_id` BIGINT FK -> `projects.id` NOT NULL
- `assignee_name` VARCHAR(100) NOT NULL
- `task` TEXT NOT NULL
- `due_date` DATE NULL
- `created_at` TIMESTAMP NOT NULL
