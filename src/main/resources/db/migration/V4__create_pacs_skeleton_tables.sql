CREATE TABLE patients (
    id            BIGSERIAL PRIMARY KEY,
    hospital_id   BIGINT NOT NULL REFERENCES hospitals(id),
    patient_uid   VARCHAR(100) NOT NULL,
    name          VARCHAR(255),
    gender        VARCHAR(10),
    date_of_birth DATE,
    is_active     SMALLINT NOT NULL DEFAULT 1,
    created       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified      TIMESTAMPTZ,
    UNIQUE(hospital_id, patient_uid)
);

CREATE TABLE pacs_studies (
    id                  BIGSERIAL PRIMARY KEY,
    hospital_id         BIGINT NOT NULL REFERENCES hospitals(id),
    patient_id          BIGINT NOT NULL REFERENCES patients(id),
    study_instance_uid  VARCHAR(200) NOT NULL UNIQUE,
    accession_number    VARCHAR(100),
    modality            VARCHAR(20),
    study_date          DATE,
    study_description   TEXT,
    assigned_to         BIGINT REFERENCES users(id),
    status              VARCHAR(50) DEFAULT 'PENDING',
    is_active           SMALLINT NOT NULL DEFAULT 1,
    created             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified            TIMESTAMPTZ
);

CREATE TABLE pacs_patient_queue (
    id          BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
    patient_id  BIGINT NOT NULL REFERENCES patients(id),
    study_id    BIGINT REFERENCES pacs_studies(id),
    status      VARCHAR(50) NOT NULL DEFAULT 'WAITING',
    assigned_to BIGINT REFERENCES users(id),
    notes       TEXT,
    created     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified    TIMESTAMPTZ
);

CREATE TABLE pacs_viewer_sessions (
    id          BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT NOT NULL REFERENCES hospitals(id),
    user_id     BIGINT NOT NULL REFERENCES users(id),
    study_id    BIGINT NOT NULL REFERENCES pacs_studies(id),
    token       VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    ip_address  VARCHAR(80),
    created     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
