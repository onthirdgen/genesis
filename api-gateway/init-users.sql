-- Create users table for authentication
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    role VARCHAR(50) DEFAULT 'ANALYST',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on email for faster lookups
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Insert test users
-- Password for all test users is: password123
-- BCrypt hash generated with: new BCryptPasswordEncoder().encode("password123")
INSERT INTO users (id, email, password_hash, full_name, role, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'analyst@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye1JEQCQWmDPL3.r7RllMbZ/t5xlqX1pS', 'John Analyst', 'ANALYST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'admin@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye1JEQCQWmDPL3.r7RllMbZ/t5xlqX1pS', 'Admin User', 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'supervisor@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye1JEQCQWmDPL3.r7RllMbZ/t5xlqX1pS', 'Jane Supervisor', 'SUPERVISOR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

-- Update function for updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger to automatically update updated_at
DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
