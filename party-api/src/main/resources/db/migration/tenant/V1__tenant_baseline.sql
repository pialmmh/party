-- =========================================================
-- Per-tenant baseline (applied by ProvisionTenantWorkflow)
-- =========================================================

CREATE TABLE owndb (
  id              TINYINT(1)   NOT NULL DEFAULT 1 PRIMARY KEY,
  operator_id     BIGINT       NOT NULL,
  operator_short  VARCHAR(40)  NOT NULL,
  tenant_id       BIGINT       NOT NULL,
  tenant_short    VARCHAR(40)  NOT NULL,
  provisioned_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT ck_owndb_singleton CHECK (id = 1)
) ENGINE=InnoDB;

CREATE TABLE partner (
  id              BIGINT       NOT NULL PRIMARY KEY,
  partner_name    VARCHAR(120) NOT NULL UNIQUE,
  alternate_name_invoice           VARCHAR(400),
  alternate_name_other             VARCHAR(400),
  address1        VARCHAR(100),
  address2        VARCHAR(100),
  city            VARCHAR(45),
  state           VARCHAR(45),
  postal_code     VARCHAR(45),
  country         VARCHAR(45),
  telephone       VARCHAR(45),
  email           VARCHAR(120),
  customer_prepaid                 TINYINT(1)   NOT NULL DEFAULT 1,
  partner_type    VARCHAR(30)  NOT NULL DEFAULT 'DIRECT',
  billing_date    INT,
  allowed_days_for_invoice_payment INT,
  timezone_offset_minutes          INT,
  call_src_id     INT,
  default_currency INT         NOT NULL DEFAULT 1,
  invoice_address VARCHAR(200),
  vat_registration_no VARCHAR(45),
  payment_advice  VARCHAR(1000),
  status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

CREATE TABLE partner_extra (
  id              BIGINT       NOT NULL PRIMARY KEY,
  partner_id      BIGINT       NOT NULL UNIQUE,
  address1        VARCHAR(200),
  address2        VARCHAR(200),
  address3        VARCHAR(200),
  address4        VARCHAR(200),
  city            VARCHAR(80),
  state           VARCHAR(80),
  postal_code     VARCHAR(40),
  nid             VARCHAR(40),
  trade_license   VARCHAR(80),
  tin             VARCHAR(40),
  tax_return_date DATE,
  country_code    VARCHAR(5),
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_pe_partner FOREIGN KEY (partner_id) REFERENCES partner(id)
) ENGINE=InnoDB;

CREATE TABLE auth_user (
  id              BIGINT       NOT NULL PRIMARY KEY,
  partner_id      BIGINT       NOT NULL,
  email           VARCHAR(160) NOT NULL UNIQUE,
  password_hash   VARCHAR(200) NOT NULL,
  first_name      VARCHAR(80),
  last_name       VARCHAR(80),
  phone           VARCHAR(40),
  user_status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  reseller_db_name VARCHAR(120),
  pbx_uuid        VARCHAR(60),
  last_login_at   DATETIME(3),
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_user_partner (partner_id),
  CONSTRAINT fk_user_partner FOREIGN KEY (partner_id) REFERENCES partner(id)
) ENGINE=InnoDB;

CREATE TABLE auth_role (
  id              BIGINT       NOT NULL PRIMARY KEY,
  name            VARCHAR(80)  NOT NULL UNIQUE,
  description     VARCHAR(240),
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

CREATE TABLE auth_permission (
  id              BIGINT       NOT NULL PRIMARY KEY,
  name            VARCHAR(120) NOT NULL UNIQUE,
  description     VARCHAR(240),
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

CREATE TABLE auth_user_role (
  user_id         BIGINT       NOT NULL,
  role_id         BIGINT       NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_aur_user FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_aur_role FOREIGN KEY (role_id) REFERENCES auth_role(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE auth_role_permission (
  role_id         BIGINT       NOT NULL,
  permission_id   BIGINT       NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_arp_role FOREIGN KEY (role_id) REFERENCES auth_role(id) ON DELETE CASCADE,
  CONSTRAINT fk_arp_perm FOREIGN KEY (permission_id) REFERENCES auth_permission(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE ip_access_rule (
  id              BIGINT       NOT NULL PRIMARY KEY,
  user_id         BIGINT       NOT NULL,
  ip              VARCHAR(45)  NOT NULL,
  permission_type VARCHAR(10)  NOT NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_iar_user (user_id),
  CONSTRAINT fk_iar_user FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE ui_menu_permission (
  id              BIGINT       NOT NULL PRIMARY KEY,
  user_id         BIGINT       NOT NULL,
  menu_key        VARCHAR(100) NOT NULL,
  permission_level VARCHAR(20) NOT NULL DEFAULT 'READONLY',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_ump_user_menu (user_id, menu_key),
  CONSTRAINT fk_ump_user FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE
) ENGINE=InnoDB;
