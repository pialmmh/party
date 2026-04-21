-- Bootstrap sys admin; password = 'change-me-now' (BCrypt cost 12).
-- Rotate immediately on first deployment via POST /api/v1/auth/reset-initial-password.
INSERT INTO operator_user (email, password_hash, first_name, last_name, role, status)
VALUES ('sysadmin@telcobright.local',
        '$2a$12$6GZlkY.wjKZz1.eF6n9Do.NRJ5oJgvLBqS2aWp1r3VbqOjJ4GOVxu',
        'System', 'Admin', 'SYS_ADMIN', 'ACTIVE');
