-- ============================================================
-- LIFEFORGE DATABASE SCHEMA (FIXED FOR JAVA COMPATIBILITY)
-- ============================================================

DROP TABLE IF EXISTS password_resets CASCADE;
DROP TABLE IF EXISTS audit_logs CASCADE;
DROP TABLE IF EXISTS saved_recommendations CASCADE;
DROP TABLE IF EXISTS recommendations CASCADE;
DROP TABLE IF EXISTS recommendation_categories CASCADE;
DROP TABLE IF EXISTS user_goals CASCADE;
DROP TABLE IF EXISTS goals CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- ------------------------------------------------------------
-- USERS TABLE
-- ------------------------------------------------------------
CREATE TABLE users (
                       id              BIGSERIAL PRIMARY KEY,
                       full_name       VARCHAR(100)    NOT NULL,
                       username        VARCHAR(100)    UNIQUE,
                       email           VARCHAR(150)    NOT NULL UNIQUE,
                       password_hash   VARCHAR(255)    NOT NULL,
                       age             INTEGER         NOT NULL CHECK (age BETWEEN 13 AND 100),
                       gender          VARCHAR(30)     NOT NULL,
                       height_cm       DOUBLE PRECISION NOT NULL CHECK (height_cm BETWEEN 100 AND 250),
                       weight_kg       DOUBLE PRECISION NOT NULL CHECK (weight_kg BETWEEN 30 AND 300),
                       activity_level  VARCHAR(50)     NOT NULL,
                       role            VARCHAR(30)     NOT NULL DEFAULT 'USER',
                       blocked         BOOLEAN         NOT NULL DEFAULT FALSE,
                       created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
                       updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_role ON users(role);

-- ------------------------------------------------------------
-- GOALS TABLE
-- ------------------------------------------------------------
CREATE TABLE goals (
                       id              BIGSERIAL PRIMARY KEY,
                       code            VARCHAR(50)     NOT NULL UNIQUE,
                       name            VARCHAR(100)    NOT NULL,
                       description     TEXT,
                       active          BOOLEAN         NOT NULL DEFAULT TRUE
);

-- ------------------------------------------------------------
-- USER_GOALS
-- ------------------------------------------------------------
CREATE TABLE user_goals (
                            id              BIGSERIAL PRIMARY KEY,
                            user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            goal_id         BIGINT          NOT NULL REFERENCES goals(id) ON DELETE RESTRICT,
                            active          BOOLEAN         NOT NULL DEFAULT TRUE,
                            selected_at     TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_goals_user_active ON user_goals(user_id, active);

-- ------------------------------------------------------------
-- RECOMMENDATION_CATEGORIES
-- ------------------------------------------------------------
CREATE TABLE recommendation_categories (
                                           id                  BIGSERIAL PRIMARY KEY,
                                           name                VARCHAR(100)    NOT NULL,
                                           description         TEXT,
                                           parent_category_id  BIGINT REFERENCES recommendation_categories(id) ON DELETE CASCADE,
                                           display_order       INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_categories_parent ON recommendation_categories(parent_category_id);

-- ------------------------------------------------------------
-- RECOMMENDATIONS
-- ------------------------------------------------------------
CREATE TABLE recommendations (
                                 id                      BIGSERIAL PRIMARY KEY,
                                 goal_id                 BIGINT NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
                                 category_id             BIGINT NOT NULL REFERENCES recommendation_categories(id) ON DELETE CASCADE,
                                 activity_level          VARCHAR(50),
                                 title                   VARCHAR(150) NOT NULL,
                                 description             TEXT,
                                 recommended_actions     TEXT,
                                 suggested_target        VARCHAR(150),
                                 examples                TEXT,
                                 important_notes         TEXT,
                                 created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
                                 updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recommendations_goal_category ON recommendations(goal_id, category_id);
CREATE INDEX idx_recommendations_activity_level ON recommendations(activity_level);

-- ------------------------------------------------------------
-- SAVED_RECOMMENDATIONS
-- ------------------------------------------------------------
CREATE TABLE saved_recommendations (
                                       id                  BIGSERIAL PRIMARY KEY,
                                       user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                       recommendation_id   BIGINT NOT NULL REFERENCES recommendations(id) ON DELETE CASCADE,
                                       saved_at            TIMESTAMP NOT NULL DEFAULT NOW(),
                                       UNIQUE (user_id, recommendation_id)
);

CREATE INDEX idx_saved_recommendations_user ON saved_recommendations(user_id);

-- ------------------------------------------------------------
-- AUDIT_LOGS
-- ------------------------------------------------------------
CREATE TABLE audit_logs (
                            id              BIGSERIAL PRIMARY KEY,
                            actor_user_id   BIGINT REFERENCES users(id) ON DELETE SET NULL,
                            action          VARCHAR(50) NOT NULL,
                            target_type     VARCHAR(50),
                            target_id       BIGINT,
                            details         TEXT,
                            created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- ------------------------------------------------------------
-- PASSWORD_RESETS (Forgot Password / verification codes)
-- ------------------------------------------------------------
CREATE TABLE password_resets (
                                  id              BIGSERIAL PRIMARY KEY,
                                  user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                  code_hash       VARCHAR(255)    NOT NULL,
                                  expires_at      TIMESTAMP       NOT NULL,
                                  attempt_count   INTEGER         NOT NULL DEFAULT 0,
                                  used            BOOLEAN         NOT NULL DEFAULT FALSE,
                                  status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
                                  created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_resets_user ON password_resets(user_id);
CREATE INDEX idx_password_resets_status ON password_resets(status);

-- NOTE: if you already have a populated database and do NOT want to
-- re-run the whole schema, apply the single table above to your existing
-- database with:  CREATE TABLE password_resets ( ... );  plus the index.