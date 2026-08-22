-- Phase 3: authentication schema only. Do not add academic/lab/scheduling
-- tables here - those arrive with their own migrations starting Phase 4
-- (see docs/04-DATABASE-DESIGN.md).
--
-- Table is named app_user, not "user", since "user" collides with SQL/Postgres
-- reserved terminology. Email uniqueness is enforced here at the database
-- level (not just application validation, per docs/09-AUTHORIZATION-RBAC.md) -
-- application code normalizes (trim + lowercase) email before every write, so
-- a plain UNIQUE constraint on the stored value is sufficient without a
-- PostgreSQL citext extension.

CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    display_name  VARCHAR(255),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT chk_app_user_role CHECK (role IN ('LAB_ASSISTANT', 'CR', 'STUDENT'))
);

-- Every authenticated request resolves the caller by id (from the JWT
-- subject), and login looks the user up by email - both are covered by the
-- primary key and the unique constraint's implicit index respectively, so no
-- additional index is added here.
