ALTER TABLE external_resources ADD COLUMN review_score integer;
ALTER TABLE external_resources ADD COLUMN review_tier varchar(32);
ALTER TABLE external_resources ADD COLUMN review_status varchar(32) NOT NULL DEFAULT 'UNREVIEWED';
ALTER TABLE external_resources ADD COLUMN review_reason text;

CREATE TABLE hidden_resource_sources (
    host varchar(253) PRIMARY KEY
);
