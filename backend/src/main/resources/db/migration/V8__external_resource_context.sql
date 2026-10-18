ALTER TABLE external_resources DROP CONSTRAINT IF EXISTS uk_external_resource_url;

CREATE UNIQUE INDEX IF NOT EXISTS uk_external_resource_url_context
    ON external_resources (url, COALESCE(local_markdown_path, ''));
