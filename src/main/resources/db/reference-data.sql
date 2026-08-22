INSERT INTO roles (name, display_name, description, is_system_role, priority) VALUES
('super_admin', 'Super Administrator', 'Super administrator with full system access', true, 1000),
('admin', 'Administrator', 'Full system access and administration', true, 100),
('manager', 'Manager', 'Hotel operations management', true, 80),
('receptionist', 'Receptionist', 'Front desk and booking management', true, 60),
('housekeeping', 'Housekeeping', 'Room cleaning and maintenance operations', true, 45),
('staff', 'Staff', 'Basic hotel staff access', true, 40),
('guest', 'Guest', 'Guest user access', true, 20),
('compliance_admin', 'Compliance Administrator', 'Compliance administration and eKYC oversight', true, 90),
('ekyc_reviewer', 'eKYC Reviewer', 'Reviews and actions assigned eKYC applications', true, 70),
('senior_reviewer', 'Senior Reviewer', 'Second-level eKYC review and high-risk approvals', true, 75),
('auditor', 'Auditor', 'Read-only audit and compliance access', true, 65),
('support_readonly', 'Read-only Support', 'Read-only operational support access', true, 30)
ON CONFLICT (name) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    is_system_role = EXCLUDED.is_system_role,
    priority = EXCLUDED.priority,
    updated_at = CURRENT_TIMESTAMP
WHERE roles.display_name IS DISTINCT FROM EXCLUDED.display_name
   OR roles.description IS DISTINCT FROM EXCLUDED.description
   OR roles.is_system_role IS DISTINCT FROM EXCLUDED.is_system_role
   OR roles.priority IS DISTINCT FROM EXCLUDED.priority;

INSERT INTO permissions (name, resource, action, description, is_system_permission) VALUES
('audit:export', 'audit', 'export', 'Export audit logs', true),
('loyalty:read', 'loyalty', 'read', 'View loyalty programme data', true),
('loyalty:manage', 'loyalty', 'manage', 'Full control over the loyalty programme', true),
('rooms:write', 'rooms', 'write', 'Create or modify rooms', true),
('permissions:create', 'permissions', 'create', 'Create permissions', true),
('permissions:read', 'permissions', 'read', 'View permissions', true),
('permissions:update', 'permissions', 'update', 'Update permissions', true),
('permissions:delete', 'permissions', 'delete', 'Delete permissions', true),
('teams:create', 'teams', 'create', 'Create teams', true),
('teams:read', 'teams', 'read', 'View teams and their membership', true),
('teams:update', 'teams', 'update', 'Update team details', true),
('teams:delete', 'teams', 'delete', 'Delete teams', true),
('teams:assign', 'teams', 'assign', 'Add or remove team members', true),
('teams:manage', 'teams', 'manage', 'Full control over teams', true),
('users:create', 'users', 'create', 'Create new users', true),
('users:read', 'users', 'read', 'View user information', true),
('users:update', 'users', 'update', 'Update user information', true),
('users:delete', 'users', 'delete', 'Delete users', true),
('users:manage', 'users', 'manage', 'Full user management', true),
('roles:create', 'roles', 'create', 'Create new roles', true),
('roles:read', 'roles', 'read', 'View roles', true),
('roles:update', 'roles', 'update', 'Update roles', true),
('roles:delete', 'roles', 'delete', 'Delete roles', true),
('roles:manage', 'roles', 'manage', 'Full role management', true),
('permissions:manage', 'permissions', 'manage', 'Full permission management access', true),
('rooms:create', 'rooms', 'create', 'Create new rooms', true),
('rooms:read', 'rooms', 'read', 'View room information', true),
('rooms:update', 'rooms', 'update', 'Update room information', true),
('rooms:delete', 'rooms', 'delete', 'Delete rooms', true),
('rooms:manage', 'rooms', 'manage', 'Full room management', true),
('housekeeping:read', 'housekeeping', 'read', 'View housekeeping tasks and board', true),
('housekeeping:create', 'housekeeping', 'create', 'Create housekeeping tasks', true),
('housekeeping:update', 'housekeeping', 'update', 'Update housekeeping task status and assignments', true),
('housekeeping:manage', 'housekeeping', 'manage', 'Full housekeeping management', true),
('maintenance:read', 'maintenance', 'read', 'View maintenance tickets', true),
('maintenance:write', 'maintenance', 'write', 'Create and update maintenance tickets', true),
('maintenance:manage', 'maintenance', 'manage', 'Full maintenance management', true),
('navigation_housekeeping:read', 'navigation:housekeeping', 'read', 'Show Housekeeping navigation', true),
('support:read', 'support', 'read', 'View guest support conversations', true),
('support:write', 'support', 'write', 'Reply to and resolve assigned guest support conversations', true),
('support:assign', 'support', 'assign', 'Claim, assign, and hand off guest support conversations', true),
('support:escalate', 'support', 'escalate', 'Escalate guest support conversations', true),
('support:manage', 'support', 'manage', 'Full guest support management', true),
('navigation_support:read', 'navigation:support', 'read', 'Show Support navigation', true),
('promotions:read', 'promotions', 'read', 'View promotions and promotion performance', true),
('promotions:manage', 'promotions', 'manage', 'Create and manage promotions', true),
('vouchers:read', 'vouchers', 'read', 'View issued vouchers and redemptions', true),
('vouchers:manage', 'vouchers', 'manage', 'Issue, revoke, and manage vouchers', true),
('navigation_promotions:read', 'navigation:promotions', 'read', 'Show Promotions navigation', true),
('communications:read', 'communications', 'read', 'View communications campaigns, templates, and delivery status', true),
('communications:compose', 'communications', 'write', 'Draft and edit email campaigns and templates', true),
('communications:send', 'communications', 'execute', 'Schedule, test-send, and send email campaigns', true),
('communications:manage', 'communications', 'manage', 'Full communications management including automation and suppressions', true),
('navigation_communications:read', 'navigation:communications', 'read', 'Show Communications navigation', true),
('bookings:create', 'bookings', 'create', 'Create new bookings', true),
('bookings:read', 'bookings', 'read', 'View bookings', true),
('bookings:update', 'bookings', 'update', 'Update bookings', true),
('bookings:delete', 'bookings', 'delete', 'Cancel bookings', true),
('bookings:manage', 'bookings', 'manage', 'Full booking management', true),
('companies:read', 'companies', 'read', 'View corporate billing accounts', true),
('companies:create', 'companies', 'create', 'Create corporate billing accounts', true),
('companies:update', 'companies', 'update', 'Update corporate billing accounts', true),
('companies:delete', 'companies', 'delete', 'Delete corporate billing accounts', true),
('companies:manage', 'companies', 'manage', 'Full corporate billing account management', true),
('guests:create', 'guests', 'create', 'Create guest profiles', true),
('guests:read', 'guests', 'read', 'View guest information', true),
('guests:update', 'guests', 'update', 'Update guest information', true),
('guests:delete', 'guests', 'delete', 'Delete guest profiles', true),
('guests:manage', 'guests', 'manage', 'Full guest management', true),
('payments:approve', 'payments', 'approve', 'Approve or reject pending payments', true),
('payments:create', 'payments', 'create', 'Process payments', true),
('payments:read', 'payments', 'read', 'View payment information', true),
('payments:update', 'payments', 'update', 'Update payments', true),
('payments:delete', 'payments', 'delete', 'Delete payment records', true),
('payments:refund', 'payments', 'refund', 'Refund and revert deposit payments', true),
('payments:manage', 'payments', 'manage', 'Full payment management', true),
('ledgers:read', 'ledgers', 'read', 'View customer ledger entries and payments', true),
('ledgers:create', 'ledgers', 'create', 'Create customer ledger entries and record ledger payments', true),
('ledgers:update', 'ledgers', 'update', 'Update customer ledger entries and payment dates', true),
('ledgers:void', 'ledgers', 'void', 'Void customer ledger entries and create reversals', true),
('ledgers:manage', 'ledgers', 'manage', 'Full customer ledger management', true),
('services:create', 'services', 'create', 'Create new services', true),
('services:read', 'services', 'read', 'View service information', true),
('services:update', 'services', 'update', 'Update services', true),
('services:delete', 'services', 'delete', 'Delete services', true),
('services:manage', 'services', 'manage', 'Full service management', true),
('reviews:create', 'reviews', 'create', 'Create reviews', true),
('reviews:read', 'reviews', 'read', 'View reviews', true),
('reviews:update', 'reviews', 'update', 'Update reviews', true),
('reviews:delete', 'reviews', 'delete', 'Delete reviews', true),
('reviews:manage', 'reviews', 'manage', 'Full review management', true),
('settings:read', 'settings', 'read', 'View system settings', true),
('settings:update', 'settings', 'update', 'Update system settings', true),
('settings:manage', 'settings', 'manage', 'Full settings management', true),
('reports:read', 'reports', 'read', 'View reports', true),
('reports:execute', 'reports', 'execute', 'Generate reports', true),
('analytics:read', 'analytics', 'read', 'Access to analytics and reports', true),
('audit:read', 'audit', 'read', 'View audit logs', true),
('night_audit:read', 'night_audit', 'read', 'View night audit data', true),
('night_audit:execute', 'night_audit', 'execute', 'Execute night audit', true),
('ekyc:read', 'ekyc', 'read', 'View masked eKYC applications', true),
('ekyc:review', 'ekyc', 'review', 'Review eKYC application details and notes', true),
('ekyc:view_sensitive', 'ekyc', 'read', 'View sensitive eKYC data when explicitly returned', true),
('ekyc:reveal_sensitive', 'ekyc', 'reveal', 'Reveal masked eKYC identity fields with audit', true),
('ekyc:download_documents', 'ekyc', 'download', 'Download private eKYC documents', true),
('ekyc:assign', 'ekyc', 'assign', 'Claim, assign, or reassign eKYC cases', true),
('ekyc:approve', 'ekyc', 'approve', 'Approve eKYC applications', true),
('ekyc:reject', 'ekyc', 'reject', 'Reject eKYC applications', true),
('ekyc:escalate', 'ekyc', 'escalate', 'Escalate eKYC applications', true),
('ekyc:request_resubmission', 'ekyc', 'request_resubmission', 'Request additional eKYC information', true),
('ekyc:override', 'ekyc', 'override', 'Perform controlled eKYC manual overrides', true),
('ekyc:export', 'ekyc', 'export', 'Export masked eKYC records', true),
('ekyc:manage_reason_codes', 'ekyc', 'manage_reason_codes', 'Manage eKYC reason codes', true),
('ekyc:manage_risk_rules', 'ekyc', 'manage_risk_rules', 'Manage eKYC risk rules', true),
('ekyc:view_provider_raw', 'ekyc', 'view_provider_raw', 'View raw eKYC provider responses', true),
('ekyc:manage', 'ekyc', 'manage', 'Full eKYC administration', true)
ON CONFLICT (name) DO UPDATE SET
    description = EXCLUDED.description,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    is_system_permission = EXCLUDED.is_system_permission
WHERE permissions.description IS DISTINCT FROM EXCLUDED.description
   OR permissions.resource IS DISTINCT FROM EXCLUDED.resource
   OR permissions.action IS DISTINCT FROM EXCLUDED.action
   OR permissions.is_system_permission IS DISTINCT FROM EXCLUDED.is_system_permission;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name IN ('admin', 'super_admin')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'auditor' AND p.name IN ('audit:read', 'audit:export')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'manager' AND p.name IN (
    'users:read', 'users:create', 'users:update', 'rooms:manage', 'bookings:manage', 'guests:manage',
    'housekeeping:read', 'housekeeping:create', 'housekeeping:update', 'housekeeping:manage',
    'maintenance:read', 'maintenance:write', 'maintenance:manage', 'navigation_housekeeping:read',
    'support:read', 'support:write', 'support:assign', 'support:escalate', 'support:manage',
    'navigation_support:read',
    'payments:manage', 'ledgers:read', 'ledgers:create', 'ledgers:update', 'ledgers:void', 'ledgers:manage',
    'companies:read', 'companies:create', 'companies:update', 'companies:delete', 'companies:manage',
    'services:manage', 'reviews:manage', 'reports:read', 'reports:execute', 'analytics:read',
    'teams:read', 'teams:assign', 'loyalty:read', 'loyalty:manage'
) ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'receptionist' AND p.name IN (
    'rooms:read', 'rooms:update', 'bookings:create', 'bookings:read', 'bookings:update',
    'housekeeping:read', 'housekeeping:create', 'housekeeping:update', 'navigation_housekeeping:read',
    'support:read', 'support:write', 'support:assign', 'support:escalate', 'navigation_support:read',
    'guests:create', 'guests:read', 'guests:update', 'guests:manage', 'payments:create', 'payments:read',
    'payments:update', 'payments:delete', 'payments:refund',
    'ledgers:read', 'ledgers:create', 'companies:read', 'companies:create',
    'services:read', 'services:create', 'reviews:read', 'settings:read',
    'analytics:read', 'reports:execute', 'teams:read'
) ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'housekeeping' AND p.name IN (
    'rooms:read', 'rooms:update',
    'housekeeping:read', 'housekeeping:create', 'housekeeping:update', 'housekeeping:manage',
    'maintenance:read', 'maintenance:write',
    'navigation_housekeeping:read', 'navigation_room_management:read', 'teams:read'
) ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'staff' AND p.name IN (
    'rooms:read', 'bookings:read', 'guests:read', 'services:read', 'services:create', 'reviews:read',
    'support:read', 'support:write', 'navigation_support:read'
) ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'guest' AND p.name IN (
    'rooms:read', 'reviews:create', 'reviews:read', 'reviews:update'
) ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'compliance_admin' AND p.name IN (
    'ekyc:read', 'ekyc:review', 'ekyc:view_sensitive', 'ekyc:reveal_sensitive',
    'ekyc:download_documents', 'ekyc:assign', 'ekyc:approve', 'ekyc:reject',
    'ekyc:escalate', 'ekyc:request_resubmission', 'ekyc:override',
    'ekyc:export', 'ekyc:manage_reason_codes', 'ekyc:manage_risk_rules',
    'ekyc:view_provider_raw', 'navigation_ekyc_admin:read', 'audit:read'
) ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ekyc_reviewer' AND p.name IN (
    'ekyc:read', 'ekyc:review', 'ekyc:download_documents', 'ekyc:assign',
    'ekyc:approve', 'ekyc:reject', 'ekyc:escalate',
    'ekyc:request_resubmission', 'navigation_ekyc_admin:read'
) ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'senior_reviewer' AND p.name IN (
    'ekyc:read', 'ekyc:review', 'ekyc:view_sensitive', 'ekyc:download_documents',
    'ekyc:assign', 'ekyc:approve', 'ekyc:reject', 'ekyc:escalate',
    'ekyc:request_resubmission', 'ekyc:override', 'navigation_ekyc_admin:read'
) ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'auditor' AND p.name IN (
    'ekyc:read', 'ekyc:export', 'navigation_ekyc_admin:read', 'audit:read'
) ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'support_readonly' AND p.name IN (
    'ekyc:read', 'navigation_ekyc_admin:read', 'support:read', 'navigation_support:read'
) ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO route_access_policies (
    route_id,
    path,
    nav_label,
    nav_group,
    required_permissions,
    required_roles,
    excluded_roles,
    nav_permissions,
    nav_roles,
    nav_excluded_roles,
    is_navigation,
    is_system_policy
)
VALUES (
    'housekeeping',
    '/housekeeping',
    'Housekeeping',
    'operations',
    '["housekeeping:read"]'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    '["navigation_housekeeping:read","housekeeping:read"]'::jsonb,
    '[]'::jsonb,
    '["guest"]'::jsonb,
    true,
    true
)
ON CONFLICT (route_id) DO UPDATE SET
    path = EXCLUDED.path,
    nav_label = EXCLUDED.nav_label,
    nav_group = EXCLUDED.nav_group,
    required_permissions = EXCLUDED.required_permissions,
    required_roles = EXCLUDED.required_roles,
    excluded_roles = EXCLUDED.excluded_roles,
    nav_permissions = EXCLUDED.nav_permissions,
    nav_roles = EXCLUDED.nav_roles,
    nav_excluded_roles = EXCLUDED.nav_excluded_roles,
    is_navigation = EXCLUDED.is_navigation,
    is_system_policy = EXCLUDED.is_system_policy,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO route_access_policies (
    route_id,
    path,
    nav_label,
    nav_group,
    required_permissions,
    required_roles,
    excluded_roles,
    nav_permissions,
    nav_roles,
    nav_excluded_roles,
    is_navigation,
    is_system_policy
)
VALUES (
    'support',
    '/support',
    'Support',
    'operations',
    '["support:read"]'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    '["navigation_support:read","support:read"]'::jsonb,
    '[]'::jsonb,
    '["guest"]'::jsonb,
    true,
    true
)
ON CONFLICT (route_id) DO UPDATE SET
    path = EXCLUDED.path,
    nav_label = EXCLUDED.nav_label,
    nav_group = EXCLUDED.nav_group,
    required_permissions = EXCLUDED.required_permissions,
    required_roles = EXCLUDED.required_roles,
    excluded_roles = EXCLUDED.excluded_roles,
    nav_permissions = EXCLUDED.nav_permissions,
    nav_roles = EXCLUDED.nav_roles,
    nav_excluded_roles = EXCLUDED.nav_excluded_roles,
    is_navigation = EXCLUDED.is_navigation,
    is_system_policy = EXCLUDED.is_system_policy,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO route_access_policies (
    route_id,
    path,
    nav_label,
    nav_group,
    required_permissions,
    required_roles,
    excluded_roles,
    nav_permissions,
    nav_roles,
    nav_excluded_roles,
    is_navigation,
    is_system_policy
)
VALUES (
    'promotions',
    '/promotions',
    'Promotions',
    'admin',
    '["promotions:read"]'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    '["navigation_promotions:read","promotions:read"]'::jsonb,
    '[]'::jsonb,
    '["guest"]'::jsonb,
    true,
    true
)
ON CONFLICT (route_id) DO UPDATE SET
    path = EXCLUDED.path,
    nav_label = EXCLUDED.nav_label,
    nav_group = EXCLUDED.nav_group,
    required_permissions = EXCLUDED.required_permissions,
    required_roles = EXCLUDED.required_roles,
    excluded_roles = EXCLUDED.excluded_roles,
    nav_permissions = EXCLUDED.nav_permissions,
    nav_roles = EXCLUDED.nav_roles,
    nav_excluded_roles = EXCLUDED.nav_excluded_roles,
    is_navigation = EXCLUDED.is_navigation,
    is_system_policy = EXCLUDED.is_system_policy,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO route_access_policies (
    route_id,
    path,
    nav_label,
    nav_group,
    required_permissions,
    required_roles,
    excluded_roles,
    nav_permissions,
    nav_roles,
    nav_excluded_roles,
    is_navigation,
    is_system_policy
)
VALUES (
    'communications',
    '/communications',
    'Communications',
    'admin',
    '["communications:read"]'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    '["navigation_communications:read","communications:read"]'::jsonb,
    '[]'::jsonb,
    '["guest"]'::jsonb,
    true,
    true
)
ON CONFLICT (route_id) DO UPDATE SET
    path = EXCLUDED.path,
    nav_label = EXCLUDED.nav_label,
    nav_group = EXCLUDED.nav_group,
    required_permissions = EXCLUDED.required_permissions,
    required_roles = EXCLUDED.required_roles,
    excluded_roles = EXCLUDED.excluded_roles,
    nav_permissions = EXCLUDED.nav_permissions,
    nav_roles = EXCLUDED.nav_roles,
    nav_excluded_roles = EXCLUDED.nav_excluded_roles,
    is_navigation = EXCLUDED.is_navigation,
    is_system_policy = EXCLUDED.is_system_policy,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_settings (key, value, value_type, category, description, is_public) VALUES
('hotel_name', 'Grand Hotel', 'string', 'general', 'Hotel name', true),
('hotel_address', '123 Main Street, City', 'string', 'general', 'Hotel address', true),
('hotel_phone', '+1-555-0123', 'string', 'general', 'Hotel contact phone', true),
('hotel_email', 'info@grandhotel.com', 'string', 'general', 'Hotel contact email', true),
('auto_checkin_requires_ekyc', 'true', 'boolean', 'frontdesk', 'Require approved guest eKYC before scheduled auto check-in', false),
('check_in_time', '15:00', 'string', 'general', 'Standard check-in time', true),
('check_out_time', '11:00', 'string', 'general', 'Standard check-out time', true),
('night_shift_time', '23:00', 'string', 'operations', 'Scheduled night audit posting time', false),
('currency', 'USD', 'string', 'general', 'Default currency code', true),
('timezone', 'Asia/Kuala_Lumpur', 'string', 'general', 'Hotel timezone', false),
('deposit_amount', '50', 'number', 'payments', 'Default room card or check-in deposit amount', false),
('tourism_tax_rate', '10', 'number', 'tax', 'Tourism tax amount charged per night for foreign guests', false),
('service_tax_rate', '8', 'number', 'tax', 'Service tax percentage applied to room charges', true),
('default_payment_terms_days', '30', 'number', 'ledger', 'Default ledger due-date offset in days when a company has no payment terms', false),
('max_login_attempts', '5', 'number', 'security', 'Maximum failed login attempts before lockout', false),
('session_timeout', '3600', 'number', 'security', 'Session timeout in seconds', false),
('enable_2fa', 'false', 'boolean', 'security', 'Enable two-factor authentication', false),
('enable_email_verification', 'true', 'boolean', 'security', 'Require email verification', false),
('totp_issuer_name', 'Hotel Management System', 'string', 'security', 'Issuer name shown in authenticator apps during TOTP setup', false),
('passkey_relying_party_name', 'Hotel Management System', 'string', 'security', 'Display name shown by passkey authenticators during registration', false),
('rate_codes', '["RACK","OVR","CORP","GOVT","WKII","PKG","GRP","AAA","PROMO"]', 'json', 'rates', 'Available rate codes', true),
('market_codes', '["WKII","CORP","GOVT","OTA","DIRECT","GROUP","EVENTS","LEISURE"]', 'json', 'sales', 'Market segment codes', true),
('booking_channels', '[{"name":"Booking.com","abbreviation":"B.C"},{"name":"Agoda","abbreviation":"A.C"},{"name":"Traveloka","abbreviation":"T.C"},{"name":"Expedia","abbreviation":"E.C"},{"name":"Hotels.com","abbreviation":"H.C"},{"name":"Airbnb","abbreviation":"AB"},{"name":"Trip.com","abbreviation":"TR"},{"name":"Direct Website","abbreviation":"DW"},{"name":"Other OTA","abbreviation":"OT"}]', 'json', 'sales', 'Online and direct booking channels available to front desk workflows', true),
('payment_methods', '["Cash","Visa Card","Master Card","Debit Card","Sarawak Pay","American Express","Bank Transfer","E-Wallet","Other"]', 'json', 'payments', 'Payment methods available to walk-in and payment workflows', true),
('report_font_size', '14', 'number', 'reports', 'Base font size in pixels for generated report previews and print output', false),
('report_font_family', 'Arial, Helvetica, sans-serif', 'string', 'reports', 'Font family for generated report previews and print output', false),
('report_heading_font_size', '24', 'number', 'reports', 'Large heading and KPI font size in pixels for generated reports', false),
('report_section_heading_font_size', '18', 'number', 'reports', 'Section heading font size in pixels for generated reports', false),
('report_table_font_size', '14', 'number', 'reports', 'Table font size in pixels for generated reports', false),
('report_caption_font_size', '13', 'number', 'reports', 'Caption and secondary label font size in pixels for generated reports', false),
('report_chip_font_size', '12', 'number', 'reports', 'Status chip font size in pixels for generated reports', false),
('support_enabled', 'true', 'boolean', 'support', 'Enable guest portal support conversations', false),
('support_categories', '["booking","stay","billing","loyalty","technical","other"]', 'json', 'support', 'Guest-selectable support conversation categories', false),
('support_first_response_low_minutes', '240', 'number', 'support', 'First-response SLA for low priority support conversations in minutes', false),
('support_first_response_normal_minutes', '60', 'number', 'support', 'First-response SLA for normal priority support conversations in minutes', false),
('support_first_response_high_minutes', '15', 'number', 'support', 'First-response SLA for high priority support conversations in minutes', false),
('support_first_response_urgent_minutes', '5', 'number', 'support', 'First-response SLA for urgent priority support conversations in minutes', false),
('support_resolution_low_minutes', '1440', 'number', 'support', 'Resolution SLA for low priority support conversations in minutes', false),
('support_resolution_normal_minutes', '480', 'number', 'support', 'Resolution SLA for normal priority support conversations in minutes', false),
('support_resolution_high_minutes', '120', 'number', 'support', 'Resolution SLA for high priority support conversations in minutes', false),
('support_resolution_urgent_minutes', '30', 'number', 'support', 'Resolution SLA for urgent priority support conversations in minutes', false),
('support_reopen_window_days', '7', 'number', 'support', 'Days a resolved guest support conversation can be reopened by its guest', false),
('guest_titles', '["Mr","Mrs","Ms","Miss","Dr","Prof","Rev"]', 'json', 'guests', 'Guest title options', true)
ON CONFLICT (key) DO UPDATE SET
    value_type = EXCLUDED.value_type,
    category = EXCLUDED.category,
    description = EXCLUDED.description,
    is_public = EXCLUDED.is_public,
    updated_at = CURRENT_TIMESTAMP
WHERE system_settings.value_type IS DISTINCT FROM EXCLUDED.value_type
   OR system_settings.category IS DISTINCT FROM EXCLUDED.category
   OR system_settings.description IS DISTINCT FROM EXCLUDED.description
   OR system_settings.is_public IS DISTINCT FROM EXCLUDED.is_public;

INSERT INTO booking_channels
    (name, channel_type, default_commission_type, default_commission_value, default_commission_scope, is_active)
VALUES
    ('Direct', 'direct', 'none', 0, 'per_booking', true),
    ('Walk-in', 'walk_in', 'none', 0, 'per_booking', true),
    ('Phone', 'phone', 'none', 0, 'per_booking', true),
    ('Direct Website', 'website', 'none', 0, 'per_booking', true),
    ('Booking.com', 'ota', 'none', 0, 'per_booking', true),
    ('Agoda', 'ota', 'none', 0, 'per_booking', true),
    ('Traveloka', 'ota', 'none', 0, 'per_booking', true),
    ('Expedia', 'ota', 'none', 0, 'per_booking', true),
    ('Hotels.com', 'ota', 'none', 0, 'per_booking', true),
    ('Airbnb', 'ota', 'none', 0, 'per_booking', true),
    ('Trip.com', 'ota', 'none', 0, 'per_booking', true),
    ('Other OTA', 'ota', 'none', 0, 'per_booking', true)
ON CONFLICT (name) DO NOTHING;

INSERT INTO loyalty_programs (name, description, points_per_dollar, currency, is_active)
SELECT 'Stay Rewards', 'Default guest loyalty program', 1.0000, 'USD', true
WHERE NOT EXISTS (SELECT 1 FROM loyalty_programs);

INSERT INTO loyalty_tiers
    (program_id, code, name, sort_order, min_points, min_nights, min_spend, benefits, is_active)
SELECT p.id, 'silver', 'Silver', 1, 0, 0, 0,
       '["Member rates","Points on eligible stays"]'::jsonb, true
FROM (SELECT id FROM loyalty_programs ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM loyalty_tiers WHERE code = 'silver');

INSERT INTO loyalty_tiers
    (program_id, code, name, sort_order, min_points, min_nights, min_spend, benefits, is_active)
SELECT p.id, 'gold', 'Gold', 2, 5000, 10, 2500,
       '["Priority support","Late checkout when available","Bonus earning"]'::jsonb, true
FROM (SELECT id FROM loyalty_programs ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM loyalty_tiers WHERE code = 'gold');

INSERT INTO loyalty_tiers
    (program_id, code, name, sort_order, min_points, min_nights, min_spend, benefits, is_active)
SELECT p.id, 'platinum', 'Platinum', 3, 15000, 30, 7500,
       '["Room upgrade priority","Welcome amenity","Highest earning rate"]'::jsonb, true
FROM (SELECT id FROM loyalty_programs ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM loyalty_tiers WHERE code = 'platinum');

INSERT INTO loyalty_program_rules
    (id, points_per_currency_unit, tier_qualification_metric, point_expiry_months,
     redemption_approval_required, earning_enabled, min_eligible_amount)
VALUES (1, 1, 'points', 24, true, true, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO system_settings (key, value, value_type, category, description, is_public)
VALUES ('guest_booking_cancellation_enabled', 'false', 'boolean', 'booking',
        'Allow guests to cancel eligible bookings in the guest portal', false)
ON CONFLICT (key) DO NOTHING;

INSERT INTO route_access_policies (
    route_id, path, nav_label, nav_group, required_permissions, required_roles,
    excluded_roles, nav_permissions, nav_roles, nav_excluded_roles, is_navigation, is_system_policy
)
VALUES (
    'online-inventory', '/online-inventory', 'Online Inventory', 'operations',
    '["rooms:update","rooms:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb,
    '["rooms:update","rooms:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true
)
ON CONFLICT (route_id) DO UPDATE SET
    path = EXCLUDED.path,
    nav_label = EXCLUDED.nav_label,
    nav_group = EXCLUDED.nav_group,
    required_permissions = EXCLUDED.required_permissions,
    nav_permissions = EXCLUDED.nav_permissions,
    is_navigation = EXCLUDED.is_navigation,
    is_system_policy = EXCLUDED.is_system_policy,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO route_access_policies (
    route_id, path, nav_label, nav_group, required_permissions, required_roles,
    excluded_roles, nav_permissions, nav_roles, nav_excluded_roles, is_navigation, is_system_policy
)
VALUES
    ('timeline', '/timeline', 'Timeline', 'main', '["rooms:read"]'::jsonb, '[]'::jsonb, '["guest"]'::jsonb, '["bookings:read"]'::jsonb, '[]'::jsonb, '["guest"]'::jsonb, true, true),
    ('guest-config', '/guest-config', 'Guests', 'main', '["guests:read","guests:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["guests:read","guests:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true),
    ('bookings', '/bookings', 'Bookings', 'main', '["bookings:read","bookings:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["bookings:read","bookings:manage"]'::jsonb, '[]'::jsonb, '["guest"]'::jsonb, true, true),
    ('room-management', '/room-management', 'Rooms', 'main', '["rooms:read","rooms:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["rooms:read","rooms:manage"]'::jsonb, '[]'::jsonb, '["guest"]'::jsonb, true, true),
    ('reports', '/reports', 'Reports', 'operations', '["analytics:read","reports:execute"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["analytics:read","reports:execute"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true),
    ('company-ledger', '/company-ledger', 'Ledger', 'operations', '["ledgers:read","ledgers:create","ledgers:update","ledgers:void","ledgers:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["ledgers:read","ledgers:create","ledgers:update","ledgers:void","ledgers:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true),
    ('room-config', '/room-config', 'Room Configuration', 'config', '["rooms:update","rooms:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["rooms:update","rooms:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true),
    ('settings', '/settings', 'Settings', 'config', '["settings:read"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["settings:read","settings:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true),
    ('rbac', '/rbac', 'Access Control', 'config', '["roles:read","roles:manage","permissions:manage","users:read","users:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["roles:read","roles:manage","permissions:manage","users:read","users:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true),
    ('night-audit', '/night-audit', 'Night Audit', 'admin', '["night_audit:read","night_audit:execute"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["night_audit:read","night_audit:execute"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true),
    ('payment-approvals', '/payment-approvals', 'Payment Approvals', 'admin', '["payments:approve","payments:read"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["payments:approve","payments:read"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true),
    ('audit-log', '/audit-log', 'Audit Log', 'admin', '["audit:read"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["audit:read"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true),
    ('complimentary', '/complimentary', 'Complimentary Nights', 'admin', '["bookings:read","bookings:update"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["bookings:read","bookings:update"]'::jsonb, '[]'::jsonb, '["guest"]'::jsonb, true, true),
    ('loyalty', '/loyalty', 'Loyalty', 'admin', '["analytics:read"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["analytics:read"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true),
    ('data-transfer', '/data-transfer', 'Data Transfer', 'admin', '["settings:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["settings:manage"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true),
    ('ekyc-admin', '/ekyc-admin', 'eKYC Admin', 'admin', '["ekyc:read"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["ekyc:read"]'::jsonb, '[]'::jsonb, '[]'::jsonb, true, true),
    ('dashboard', '/', NULL, NULL, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, false, true),
    ('profile', '/profile', NULL, NULL, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, false, true),
    ('help', '/help', NULL, NULL, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, false, true),
    ('ekyc', '/ekyc', NULL, NULL, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, false, true),
    ('teams', '/teams', 'Teams', 'config', '["teams:read"]'::jsonb, '[]'::jsonb, '[]'::jsonb, '["teams:read"]'::jsonb, '[]'::jsonb, '["guest"]'::jsonb, true, true)
ON CONFLICT (route_id) DO UPDATE SET
    path = EXCLUDED.path,
    nav_label = EXCLUDED.nav_label,
    nav_group = EXCLUDED.nav_group,
    required_permissions = EXCLUDED.required_permissions,
    required_roles = EXCLUDED.required_roles,
    excluded_roles = EXCLUDED.excluded_roles,
    nav_permissions = EXCLUDED.nav_permissions,
    nav_roles = EXCLUDED.nav_roles,
    nav_excluded_roles = EXCLUDED.nav_excluded_roles,
    is_navigation = EXCLUDED.is_navigation,
    is_system_policy = EXCLUDED.is_system_policy,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO promotions (
    slug, name, description, terms, status, promotion_kind, discount_type,
    discount_value, currency, claim_starts_at, claim_ends_at, stay_starts_on,
    stay_ends_on, min_nights, min_subtotal, per_guest_limit, is_public,
    is_cancellable, created_by, updated_by
)
SELECT
    'july-deluxe-20-loyalty', 'July Deluxe Room 20% Voucher',
    'Redeem 2,000 loyalty points for 20% off one eligible Deluxe Room booking.',
    'One voucher per guest. Claim and stay dates must be in July 2026. Valid only for Deluxe Rooms.',
    'published', 'voucher', 'percentage', 20.00, 'USD',
    '2026-07-01 00:00:00+00', '2026-07-31 23:59:59+00', '2026-07-01', '2026-07-31',
    1, 0, 1, false, true, u.id, u.id
FROM users u
WHERE u.username = 'admin'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO promotion_room_types (promotion_id, room_type_id)
SELECT p.id, rt.id
FROM promotions p
JOIN room_types rt ON rt.code = 'DLX'
WHERE p.slug = 'july-deluxe-20-loyalty'
ON CONFLICT DO NOTHING;

INSERT INTO loyalty_rewards (
    name, description, category, points_cost, requires_approval, is_active,
    valid_from, valid_to, terms_conditions
)
SELECT
    'July Deluxe Room 20% Voucher',
    'Redeem 2,000 points for a voucher worth 20% off a Deluxe Room.',
    'discount', 2000, false, true, '2026-07-01', '2026-07-31',
    'The voucher is issued immediately, may be used once, and is valid only for a Deluxe Room stay in July 2026.'
WHERE NOT EXISTS (
    SELECT 1 FROM loyalty_rewards WHERE name = 'July Deluxe Room 20% Voucher'
);
