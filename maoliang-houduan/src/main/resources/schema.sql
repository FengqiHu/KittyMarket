-- MySQL schema for the Spring Boot application. Existing tables are preserved.

CREATE TABLE IF NOT EXISTS MLuser  (
    userid INTEGER PRIMARY KEY AUTO_INCREMENT,
    username char(10) NOT NULL,
    pwd char(15) NOT NULL,
    power int NOT NULL,
    question varchar(50) NOT NULL,
    answer varchar(30) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS MLgood  (
    goodid INTEGER PRIMARY KEY AUTO_INCREMENT,
    goodname char(20) NOT NULL,
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
    FOREIGN KEY (owner) REFERENCES MLuser (userid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS MLhistorygood  (
    goodid INTEGER NOT NULL,
    goodname char(20) NOT NULL,
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
