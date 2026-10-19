-- MySQL schema for the Spring Boot application. Existing tables are preserved.

CREATE TABLE IF NOT EXISTS MLuser  (
    userid INTEGER PRIMARY KEY AUTO_INCREMENT,
    username char(10) NOT NULL,
    pwd varchar(255) NOT NULL,
    power int NOT NULL,
    question varchar(50) NOT NULL,
    answer varchar(30) NOT NULL,
    address_initialized BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uk_mluser_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Idempotent upgrades for existing accounts; legacy passwords remain readable.
SET @account_migration = IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'MLuser' AND column_name = 'pwd') < 255,
    'ALTER TABLE MLuser MODIFY pwd VARCHAR(255) NOT NULL', 'SELECT 1');
PREPARE account_stmt FROM @account_migration;
EXECUTE account_stmt;
DEALLOCATE PREPARE account_stmt;
SET @account_migration = IF(
    EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'MLuser' AND column_name = 'address_initialized'),
    'SELECT 1', 'ALTER TABLE MLuser ADD address_initialized BOOLEAN NOT NULL DEFAULT FALSE');
PREPARE account_stmt FROM @account_migration;
EXECUTE account_stmt;
DEALLOCATE PREPARE account_stmt;
SET @account_migration = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'MLuser' AND index_name = 'uk_mluser_username'),
    'SELECT 1', 'ALTER TABLE MLuser ADD UNIQUE KEY uk_mluser_username (username)');
PREPARE account_stmt FROM @account_migration;
EXECUTE account_stmt;
DEALLOCATE PREPARE account_stmt;

CREATE TABLE IF NOT EXISTS MLaddress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id INTEGER NOT NULL,
    recipient VARCHAR(10) NOT NULL,
    telephone VARCHAR(11) NOT NULL,
    address VARCHAR(99) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    KEY idx_mladdress_user (user_id, is_default),
    FOREIGN KEY (user_id) REFERENCES MLuser(userid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS MLgood  (
    goodid INTEGER PRIMARY KEY AUTO_INCREMENT,
    goodname varchar(255) NOT NULL,
    description varchar(100) NOT NULL,
    price double NOT NULL,
    picture char(100) NOT NULL,
    state int NOT NULL,
    number int NOT NULL,
    kind varchar(20) NOT NULL,
    subkind varchar(20) NOT NULL,
    owner INTEGER NOT NULL,
    calorie double NOT NULL,
    catkind varchar(100) NOT NULL,
    catage varchar(20)  NOT NULL,
    catweight varchar(20)  NOT NULL,
    KEY idx_mlgood_state_goodid (state, goodid),
    KEY idx_mlgood_state_kind_goodid (state, kind, goodid),
    FOREIGN KEY (owner) REFERENCES MLuser (userid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing databases also need the browsing indexes. These checks keep startup idempotent.
SET @buyer_all_index_migration = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'MLgood'
             AND index_name = 'idx_mlgood_state_goodid'),
    'SELECT 1',
    'ALTER TABLE MLgood ADD INDEX idx_mlgood_state_goodid (state, goodid)'
);
PREPARE buyer_all_index_stmt FROM @buyer_all_index_migration;
EXECUTE buyer_all_index_stmt;
DEALLOCATE PREPARE buyer_all_index_stmt;

SET @buyer_category_index_migration = IF(
    EXISTS(SELECT 1 FROM information_schema.statistics
           WHERE table_schema = DATABASE() AND table_name = 'MLgood'
             AND index_name = 'idx_mlgood_state_kind_goodid'),
    'SELECT 1',
    'ALTER TABLE MLgood ADD INDEX idx_mlgood_state_kind_goodid (state, kind, goodid)'
);
PREPARE buyer_category_index_stmt FROM @buyer_category_index_migration;
EXECUTE buyer_category_index_stmt;
DEALLOCATE PREPARE buyer_category_index_stmt;

CREATE TABLE IF NOT EXISTS MLhistorygood  (
    goodid INTEGER NOT NULL,
    goodname varchar(255) NOT NULL,
    description varchar(100) NOT NULL,
    price double NOT NULL,
    picture char(100) NOT NULL,
    number int NOT NULL,
    kind varchar(20) NOT NULL,
    subkind varchar(20) NOT NULL,
    createdate DATETIME NOT NULL,
    owner INTEGER NOT NULL,
    calorie double NOT NULL,
    catkind varchar(100) NOT NULL,
    catage varchar(20) NOT NULL,
    catweight varchar(20) NOT NULL,
    FOREIGN KEY (owner) REFERENCES MLuser (userid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS MLorder  (
    orderid INTEGER PRIMARY KEY AUTO_INCREMENT,
    address varchar(99),
    telephone varchar(11),
    buyername varchar(10),
    recipientname varchar(10),
    goodid int NOT NULL,
    number int NOT NULL,
    orderstate int NOT NULL,
    owner INTEGER NOT NULL,
    FOREIGN KEY (owner) REFERENCES MLuser (userid),
    FOREIGN KEY (goodid) REFERENCES MLgood (goodid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add a separate shipping recipient to databases created by older versions.
-- buyername continues to identify the account for existing order-history queries.
SET @checkout_migration = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'MLorder' AND column_name = 'recipientname'),
    'SELECT 1',
    'ALTER TABLE MLorder ADD COLUMN recipientname VARCHAR(10) NULL'
);
PREPARE checkout_migration_stmt FROM @checkout_migration;
EXECUTE checkout_migration_stmt;
DEALLOCATE PREPARE checkout_migration_stmt;

CREATE TABLE IF NOT EXISTS MLprice  (
    id INTEGER PRIMARY KEY AUTO_INCREMENT,
    goodid int NOT NULL,
    price double NOT NULL,
    change_time datetime NOT NULL,
    FOREIGN KEY (goodid) REFERENCES MLgood (goodid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS MLinfo  (
    userid INTEGER NOT NULL,
    phone char(11) NOT NULL,
    address varchar(99) NOT NULL,
    FOREIGN KEY (userid) REFERENCES MLuser (userid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS MLbuying  (
    buyingid INTEGER PRIMARY KEY AUTO_INCREMENT,
    goodid INTEGER NOT NULL,
    number int NOT NULL,
    islike int NOT NULL,
    buyer INTEGER NOT NULL,
    FOREIGN KEY (buyer) REFERENCES MLuser (userid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS MLcat  (
    catid INTEGER PRIMARY KEY AUTO_INCREMENT,
    catname char(20) NOT NULL,
    description varchar(100) NOT NULL,
    catweight double NOT NULL,
    catstate int NOT NULL,
    catage int NOT NULL,
    catkind varchar(20) NOT NULL,
    owner INTEGER NOT NULL,
    FOREIGN KEY (owner) REFERENCES MLuser (userid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A single row per account/product keeps browsing history compact across sessions.
-- No product FK: removing a product must not be blocked by browsing history.
CREATE TABLE IF NOT EXISTS MLproduct_view (
    userid INTEGER NOT NULL,
    goodid INTEGER NOT NULL,
    visits INTEGER NOT NULL DEFAULT 1,
    last_viewed DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (userid, goodid),
    KEY idx_product_view_recent (userid, last_viewed),
    FOREIGN KEY (userid) REFERENCES MLuser (userid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Reusable product vectors survive application/container restarts. No credentials are stored.
CREATE TABLE IF NOT EXISTS rag_product_embedding (
    namespace CHAR(64) NOT NULL,
    good_id INTEGER NOT NULL,
    document_hash CHAR(64) NOT NULL,
    vector_data MEDIUMBLOB NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (namespace, good_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Product changes are committed with their outbox events before RabbitMQ publishing.
CREATE TABLE IF NOT EXISTS rag_catalog_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    good_id INTEGER NOT NULL,
    operation VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AI customer service conversation history (Dify + local session persistence).
CREATE TABLE IF NOT EXISTS MLcs_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_key VARCHAR(64) NOT NULL,
    userid INTEGER NULL,
    title VARCHAR(100) NOT NULL,
    dify_conversation_id VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_mlcs_conv_user (user_key, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS MLcs_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    intent VARCHAR(32) NULL,
    recommendations_json TEXT NULL,
    orders_json TEXT NULL,
    provider VARCHAR(32) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_mlcs_msg_conv (conversation_id, id),
    FOREIGN KEY (conversation_id) REFERENCES MLcs_conversation (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
