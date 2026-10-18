CREATE TABLE learning_sessions (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    goal VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_round INTEGER NOT NULL DEFAULT 0,
    mastery_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.8,
    point_floor DOUBLE PRECISION NOT NULL DEFAULT 0.6,
    max_rounds INTEGER NOT NULL DEFAULT 3,
    web_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    weak_points_json TEXT NOT NULL DEFAULT '[]',
    last_action VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_learning_sessions_course_status ON learning_sessions(course_id, status);

CREATE TABLE learning_rounds (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES learning_sessions(id) ON DELETE CASCADE,
    quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    round_number INTEGER NOT NULL,
    focus VARCHAR(240),
    strategy VARCHAR(32) NOT NULL,
    overall_accuracy DOUBLE PRECISION,
    mastered BOOLEAN NOT NULL DEFAULT FALSE,
    point_scores_json TEXT NOT NULL DEFAULT '{}',
    recommendations_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    CONSTRAINT uk_learning_round_session_number UNIQUE(session_id, round_number),
    CONSTRAINT uk_learning_round_quiz UNIQUE(quiz_id)
);

CREATE TABLE mastery_snapshots (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES learning_sessions(id) ON DELETE CASCADE,
    round_id UUID NOT NULL REFERENCES learning_rounds(id) ON DELETE CASCADE,
    knowledge_point VARCHAR(160) NOT NULL,
    attempt_count INTEGER NOT NULL,
    correct_count INTEGER NOT NULL,
    accuracy DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_mastery_session_point ON mastery_snapshots(session_id, knowledge_point);

ALTER TABLE quizzes ADD COLUMN learning_session_id UUID REFERENCES learning_sessions(id) ON DELETE SET NULL;
ALTER TABLE quizzes ADD COLUMN learning_round_number INTEGER;
ALTER TABLE quizzes ADD COLUMN source_snapshot_json TEXT;
ALTER TABLE questions ADD COLUMN source_json TEXT;

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(240) NOT NULL,
    body TEXT NOT NULL,
    action_url VARCHAR(1000),
    reference_type VARCHAR(64),
    reference_id UUID,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    dedup_key VARCHAR(160) NOT NULL UNIQUE
);
CREATE INDEX idx_notifications_created ON notifications(created_at DESC);

CREATE TABLE daily_digests (
    id UUID PRIMARY KEY,
    digest_date DATE NOT NULL UNIQUE,
    course_id UUID REFERENCES courses(id) ON DELETE SET NULL,
    topic VARCHAR(300) NOT NULL,
    markdown_path VARCHAR(1000) NOT NULL,
    summary TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE external_resources (
    id UUID PRIMARY KEY,
    digest_id UUID REFERENCES daily_digests(id) ON DELETE CASCADE,
    course_id UUID REFERENCES courses(id) ON DELETE SET NULL,
    title VARCHAR(500) NOT NULL,
    url VARCHAR(2000) NOT NULL,
    source_name VARCHAR(240),
    summary TEXT NOT NULL,
    published_at TIMESTAMPTZ,
    discovered_at TIMESTAMPTZ NOT NULL,
    local_markdown_path VARCHAR(1000),
    CONSTRAINT uk_external_resource_url UNIQUE(url)
);

CREATE TABLE workspace_projects (
    id UUID PRIMARY KEY,
    course_id UUID REFERENCES courses(id) ON DELETE SET NULL,
    name VARCHAR(240) NOT NULL,
    slug VARCHAR(160) NOT NULL UNIQUE,
    template VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    root_path VARCHAR(1000) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE workspace_artifacts (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspace_projects(id) ON DELETE CASCADE,
    artifact_type VARCHAR(48) NOT NULL,
    relative_path VARCHAR(1000) NOT NULL,
    source_url VARCHAR(2000),
    checksum VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_workspace_artifact_path UNIQUE(workspace_id, relative_path)
);

CREATE TABLE agent_jobs (
    id UUID PRIMARY KEY,
    job_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    input_json TEXT NOT NULL DEFAULT '{}',
    result_json TEXT,
    log_text TEXT NOT NULL DEFAULT '',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    dedup_key VARCHAR(200) UNIQUE
);
CREATE INDEX idx_agent_jobs_created ON agent_jobs(created_at DESC);

ALTER TABLE weekly_reviews ADD COLUMN details_json TEXT NOT NULL DEFAULT '{}';
