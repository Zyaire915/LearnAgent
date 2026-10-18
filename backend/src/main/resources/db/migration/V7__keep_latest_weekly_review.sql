DELETE FROM weekly_reviews
WHERE id NOT IN (
    SELECT id
    FROM weekly_reviews
    ORDER BY generated_at DESC, id DESC
    LIMIT 1
);
