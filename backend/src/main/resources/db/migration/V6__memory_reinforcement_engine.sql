CREATE TABLE flashcards (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    source_quiz_id UUID REFERENCES quizzes(id) ON DELETE SET NULL,
    source_question_id UUID REFERENCES questions(id) ON DELETE SET NULL,
    source_document_id UUID REFERENCES documents(id) ON DELETE SET NULL,
    card_type VARCHAR(24) NOT NULL,
    front TEXT NOT NULL,
    back TEXT NOT NULL,
    knowledge_point VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL,
    source_json TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    suspended_at TIMESTAMPTZ,
    CONSTRAINT uk_flashcard_course_hash UNIQUE(course_id, content_hash)
);
CREATE INDEX idx_flashcards_course_status ON flashcards(course_id, status);

CREATE TABLE flashcard_review_states (
    card_id UUID PRIMARY KEY REFERENCES flashcards(id) ON DELETE CASCADE,
    fsrs_card_json TEXT NOT NULL,
    difficulty DOUBLE PRECISION,
    stability DOUBLE PRECISION,
    retrievability DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    due_at TIMESTAMPTZ NOT NULL,
    last_review_at TIMESTAMPTZ,
    review_count INTEGER NOT NULL DEFAULT 0,
    lapse_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_flashcard_review_due ON flashcard_review_states(due_at, retrievability);

CREATE TABLE memory_review_sessions (
    id UUID PRIMARY KEY,
    review_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL,
    planned_count INTEGER NOT NULL,
    reviewed_count INTEGER NOT NULL DEFAULT 0,
    correct_count INTEGER NOT NULL DEFAULT 0,
    elapsed_seconds INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_memory_sessions_date ON memory_review_sessions(review_date DESC);

CREATE TABLE flashcard_review_logs (
    id UUID PRIMARY KEY,
    card_id UUID NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE,
    session_id UUID REFERENCES memory_review_sessions(id) ON DELETE SET NULL,
    rating VARCHAR(16) NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL,
    duration_seconds INTEGER NOT NULL,
    due_before TIMESTAMPTZ NOT NULL,
    due_after TIMESTAMPTZ NOT NULL,
    scheduled_days INTEGER NOT NULL,
    state_before_json TEXT NOT NULL,
    state_after_json TEXT NOT NULL
);
CREATE INDEX idx_flashcard_logs_card_time ON flashcard_review_logs(card_id, reviewed_at DESC);

CREATE TABLE knowledge_nodes (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    normalized_key VARCHAR(200) NOT NULL,
    mastery DOUBLE PRECISION NOT NULL DEFAULT 0,
    retrievability DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    forgetting_risk DOUBLE PRECISION NOT NULL DEFAULT 0,
    source_json TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_knowledge_node_course_key UNIQUE(course_id, normalized_key)
);

CREATE TABLE knowledge_edges (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    from_node_id UUID NOT NULL REFERENCES knowledge_nodes(id) ON DELETE CASCADE,
    to_node_id UUID NOT NULL REFERENCES knowledge_nodes(id) ON DELETE CASCADE,
    relation VARCHAR(24) NOT NULL,
    source_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_knowledge_edge UNIQUE(course_id, from_node_id, to_node_id, relation)
);

ALTER TABLE weekly_reviews ADD COLUMN memory_statistics_json TEXT NOT NULL DEFAULT '{}';
