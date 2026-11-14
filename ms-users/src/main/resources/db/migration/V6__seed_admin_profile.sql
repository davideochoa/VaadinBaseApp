-- Profile for ms-security's bootstrap account (V3__SeedBootstrapAdmin there: username "admin",
-- email "admin@local", role ADMINISTRATOR). The two sides are linked by username only:
-- ms-security keeps user_id NULL and never calls ms-users for local login (Lesson 5).
-- Idempotent: skipped if a row with this username or email already exists.
INSERT INTO app_user (username, first_name, last_name, email, active, user_type_id)
SELECT 'admin', 'Admin', 'Administrator', 'admin@local', TRUE, ut.id
FROM user_type ut
WHERE ut.name = 'Administrator'
  AND NOT EXISTS (
      SELECT 1 FROM app_user u
      WHERE LOWER(u.username) = 'admin' OR LOWER(u.email) = 'admin@local'
  );
