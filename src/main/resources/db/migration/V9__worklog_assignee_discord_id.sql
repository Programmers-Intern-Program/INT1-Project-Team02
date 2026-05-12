ALTER TABLE work_logs
    ADD COLUMN IF NOT EXISTS assignee_discord_id VARCHAR(50);
