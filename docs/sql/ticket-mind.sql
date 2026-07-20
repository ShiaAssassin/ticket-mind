CREATE TABLE `chat_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `title` VARCHAR(120) NOT NULL DEFAULT 'New Conversation',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`)
);

CREATE TABLE `chat_messages` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `session_id` BIGINT NOT NULL,
    `role` VARCHAR(20) NOT NULL,
    `content` LONGTEXT NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    INDEX `idx_chat_messages_session_created` (`session_id`, `created_at`, `id`)
);

CREATE TABLE `knowledge_chunks` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `source` VARCHAR(180) NOT NULL,
    `source_index` INT NOT NULL DEFAULT 0,
    `content` LONGTEXT NOT NULL,
    `embedding_json` LONGTEXT,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`)
);

CREATE TABLE `user_accounts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(80) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `display_name` VARCHAR(120) NOT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`)
);
