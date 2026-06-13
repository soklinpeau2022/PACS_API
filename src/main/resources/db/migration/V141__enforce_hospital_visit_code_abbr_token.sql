CREATE OR REPLACE FUNCTION public.hospital_visit_code_token(raw_value TEXT)
RETURNS TEXT AS $$
DECLARE
    normalized TEXT;
    compact TEXT;
    token TEXT;
BEGIN
    normalized := UPPER(TRIM(COALESCE(raw_value, '')));
    IF normalized <> '' THEN
        FOREACH token IN ARRAY regexp_split_to_array(normalized, '[^A-Z0-9]+') LOOP
            IF token ~ '^[A-Z0-9]{2,20}$'
               AND token NOT IN ('HOSPITAL', 'HOSP', 'CLINIC', 'CENTER', 'CENTRE') THEN
                RETURN token;
            END IF;
        END LOOP;
    END IF;

    compact := regexp_replace(normalized, '[^A-Z0-9]', '', 'g');
    IF compact = '' THEN
        RETURN '';
    END IF;
    RETURN LEFT(compact, 20);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

DROP INDEX IF EXISTS ux_hospitals_active_visit_code_token;

CREATE UNIQUE INDEX IF NOT EXISTS ux_hospitals_active_visit_code_token
    ON hospitals (public.hospital_visit_code_token(abbr))
    WHERE is_active = 1
      AND public.hospital_visit_code_token(abbr) <> '';
