-- Base tables required by the current entities, mappers and data.sql.
-- Username/password/email/title lengths below are starter defaults: the Java
-- model does not currently define max lengths. Review against any existing
-- database before applying this to a production schema.
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt password hash',
    email VARCHAR(255) NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    status INT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS problem (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    difficulty VARCHAR(32) NULL,
    input_description TEXT NULL,
    output_description TEXT NULL,
    sample_input TEXT NULL,
    sample_output TEXT NULL,
    time_limit INT NULL COMMENT 'Time limit in milliseconds',
    memory_limit INT NULL COMMENT 'Memory limit in megabytes',
    test_cases LONGTEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_problem_title (title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    answer TEXT NOT NULL,
    language VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    time_used INT NULL COMMENT '判题耗时（毫秒）',
    error_message VARCHAR(1000) NULL COMMENT '编译错误 / 运行错误等提示信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_submission_user_created (user_id, created_at),
    KEY idx_submission_problem_created (problem_id, created_at),
    CONSTRAINT fk_submission_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_submission_problem FOREIGN KEY (problem_id) REFERENCES problem (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Day 8：上面的 CREATE TABLE IF NOT EXISTS 不会给“已经建好的旧表”加字段，
-- 所以这里用 information_schema 判断一次，缺字段才 ALTER，保证脚本可以重复执行。
SET @sql_add_time_used = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE submission ADD COLUMN time_used INT NULL COMMENT ''判题耗时（毫秒）''',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'submission'
      AND COLUMN_NAME = 'time_used'
);
PREPARE stmt_add_time_used FROM @sql_add_time_used;
EXECUTE stmt_add_time_used;
DEALLOCATE PREPARE stmt_add_time_used;

SET @sql_add_error_message = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE submission ADD COLUMN error_message VARCHAR(1000) NULL COMMENT ''编译错误 / 运行错误等提示信息''',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'submission'
      AND COLUMN_NAME = 'error_message'
);
PREPARE stmt_add_error_message FROM @sql_add_error_message;
EXECUTE stmt_add_error_message;
DEALLOCATE PREPARE stmt_add_error_message;
