ALTER TABLE drools_rules ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'general';

UPDATE drools_rules SET category = 'loan'     WHERE rule_name = 'loan-approval';
UPDATE drools_rules SET category = 'discount' WHERE rule_name = 'discount';