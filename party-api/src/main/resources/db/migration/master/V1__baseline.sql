-- =========================================================
-- Party Service :: party_master baseline
-- =========================================================

-- ---------- Registry tables ----------

CREATE TABLE operator (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  short_name      VARCHAR(40)  NOT NULL UNIQUE,
  full_name       VARCHAR(200) NOT NULL,
  operator_type   VARCHAR(20)  NOT NULL,
  company_name    VARCHAR(200),
  address1        VARCHAR(200),
  city            VARCHAR(80),
  country         VARCHAR(80),
  phone           VARCHAR(40),
  email           VARCHAR(120),
  status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

CREATE TABLE tenant (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  operator_id     BIGINT       NOT NULL,
  short_name      VARCHAR(40)  NOT NULL,
  full_name       VARCHAR(200) NOT NULL,
  company_name    VARCHAR(200),
  address1        VARCHAR(200),
  city            VARCHAR(80),
  country         VARCHAR(80),
  phone           VARCHAR(40),
  email           VARCHAR(120),
  db_host         VARCHAR(120) NOT NULL,
  db_port         INT          NOT NULL DEFAULT 3306,
  db_name         VARCHAR(120) NOT NULL UNIQUE,
  db_user         VARCHAR(80)  NOT NULL,
  db_pass_ref     VARCHAR(200) NOT NULL,
  status          VARCHAR(20)  NOT NULL DEFAULT 'PROVISIONING',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_operator_short (operator_id, short_name),
  CONSTRAINT fk_tenant_operator FOREIGN KEY (operator_id) REFERENCES operator(id)
) ENGINE=InnoDB;

CREATE TABLE operator_user (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  operator_id     BIGINT       NULL,
  email           VARCHAR(160) NOT NULL UNIQUE,
  password_hash   VARCHAR(200) NOT NULL,
  first_name      VARCHAR(80),
  last_name       VARCHAR(80),
  phone           VARCHAR(40),
  role            VARCHAR(30)  NOT NULL,
  status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  last_login_at   DATETIME(3),
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_op_user_operator FOREIGN KEY (operator_id) REFERENCES operator(id)
) ENGINE=InnoDB;

CREATE TABLE tenant_sync_job (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  workflow_id     VARCHAR(200) NOT NULL,
  run_id          VARCHAR(100),
  entity_type     VARCHAR(40)  NOT NULL,
  entity_id       VARCHAR(80),
  operation       VARCHAR(20)  NOT NULL,
  status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  attempts        INT          NOT NULL DEFAULT 0,
  started_at      DATETIME(3),
  finished_at     DATETIME(3),
  error           TEXT,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_sync_tenant_status (tenant_id, status),
  INDEX idx_sync_workflow (workflow_id),
  CONSTRAINT fk_sync_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB;

-- ---------- Tenant-scoped authoritative data ----------

CREATE TABLE partner (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  partner_name    VARCHAR(120) NOT NULL,
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
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_partner_tenant_name (tenant_id, partner_name),
  INDEX idx_partner_tenant (tenant_id),
  CONSTRAINT fk_partner_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB;

CREATE TABLE partner_extra (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  partner_id      BIGINT       NOT NULL,
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
  UNIQUE KEY uk_partner_extra_partner (partner_id),
  CONSTRAINT fk_pe_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_pe_partner FOREIGN KEY (partner_id) REFERENCES partner(id)
) ENGINE=InnoDB;

CREATE TABLE auth_user (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  partner_id      BIGINT       NOT NULL,
  email           VARCHAR(160) NOT NULL,
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
  UNIQUE KEY uk_user_tenant_email (tenant_id, email),
  INDEX idx_user_partner (tenant_id, partner_id),
  CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_user_partner FOREIGN KEY (partner_id) REFERENCES partner(id)
) ENGINE=InnoDB;

CREATE TABLE auth_role (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  name            VARCHAR(80)  NOT NULL,
  description     VARCHAR(240),
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_role_tenant_name (tenant_id, name),
  CONSTRAINT fk_role_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB;

CREATE TABLE auth_permission (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  name            VARCHAR(120) NOT NULL,
  description     VARCHAR(240),
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_perm_tenant_name (tenant_id, name),
  CONSTRAINT fk_perm_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
) ENGINE=InnoDB;

CREATE TABLE auth_user_role (
  user_id         BIGINT       NOT NULL,
  role_id         BIGINT       NOT NULL,
  tenant_id       BIGINT       NOT NULL,
  PRIMARY KEY (user_id, role_id),
  INDEX idx_aur_tenant (tenant_id),
  CONSTRAINT fk_aur_user FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_aur_role FOREIGN KEY (role_id) REFERENCES auth_role(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE auth_role_permission (
  role_id         BIGINT       NOT NULL,
  permission_id   BIGINT       NOT NULL,
  tenant_id       BIGINT       NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  INDEX idx_arp_tenant (tenant_id),
  CONSTRAINT fk_arp_role FOREIGN KEY (role_id) REFERENCES auth_role(id) ON DELETE CASCADE,
  CONSTRAINT fk_arp_perm FOREIGN KEY (permission_id) REFERENCES auth_permission(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE ip_access_rule (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  ip              VARCHAR(45)  NOT NULL,
  permission_type VARCHAR(10)  NOT NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_iar_user (user_id),
  CONSTRAINT fk_iar_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_iar_user FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE ui_menu_permission (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  menu_key        VARCHAR(100) NOT NULL,
  permission_level VARCHAR(20) NOT NULL DEFAULT 'READONLY',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_ump_user_menu (user_id, menu_key),
  CONSTRAINT fk_ump_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
  CONSTRAINT fk_ump_user FOREIGN KEY (user_id) REFERENCES auth_user(id) ON DELETE CASCADE
) ENGINE=InnoDB;
