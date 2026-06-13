CREATE TABLE IF NOT EXISTS countries (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(160) NOT NULL UNIQUE,
    iso2_code  VARCHAR(2),
    iso3_code  VARCHAR(3),
    status     INT NOT NULL DEFAULT 1,
    is_active  INT NOT NULL DEFAULT 1,
    created    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_countries_name
    ON countries(name);

CREATE INDEX IF NOT EXISTS idx_countries_active
    ON countries(is_active, status);

INSERT INTO countries (name, iso2_code, iso3_code, status, is_active)
SELECT v.name, v.iso2, v.iso3, 1, 1
FROM (
    VALUES
        ('Cambodia', 'KH', 'KHM'),
        ('Thailand', 'TH', 'THA'),
        ('Viet Nam', 'VN', 'VNM'),
        ('Lao PDR', 'LA', 'LAO'),
        ('Singapore', 'SG', 'SGP')
) AS v(name, iso2, iso3)
WHERE NOT EXISTS (
    SELECT 1 FROM countries c WHERE c.name = v.name
);
