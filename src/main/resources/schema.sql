CREATE TABLE drools_rules (
                              id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                              rule_name   VARCHAR(100) NOT NULL UNIQUE,
                              description VARCHAR(500),
                              drl_content CLOB         NOT NULL,
                              active      BOOLEAN      NOT NULL DEFAULT TRUE,
                              created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                              updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);