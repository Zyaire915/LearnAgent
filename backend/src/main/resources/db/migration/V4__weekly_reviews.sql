CREATE TABLE weekly_reviews (
    id UUID PRIMARY KEY,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    summary TEXT,
    recommendation TEXT,
    status VARCHAR(24) NOT NULL,
    statistics_json TEXT NOT NULL,
    recommended_knowledge_points_json TEXT NOT NULL
);
CREATE INDEX idx_weekly_reviews_generated_at ON weekly_reviews(generated_at DESC);
CREATE INDEX idx_weekly_reviews_period ON weekly_reviews(period_start, period_end);
