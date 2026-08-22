-- Sample property catalogue: applied only when room_types is empty.
INSERT INTO room_types (name, code, description, max_occupancy, base_price, size_sqm, bed_type, bed_count, allows_extra_bed, max_extra_beds, extra_bed_charge, sort_order)
VALUES
    ('Standard Room', 'STD', 'Comfortable room with essential amenities', 2, 150.00, 25.0, 'Queen', 1, false, 0, 0.00, 1),
    ('Deluxe Room', 'DLX', 'Spacious room with premium amenities', 3, 250.00, 35.0, 'King', 1, true, 1, 50.00, 2),
    ('Suite', 'STE', 'Luxury suite with separate living area', 4, 450.00, 55.0, 'King', 1, true, 2, 75.00, 3),
    ('Family Room', 'FAM', 'Large room perfect for families with children', 6, 350.00, 45.0, 'Queen', 2, true, 2, 40.00, 4)
ON CONFLICT (code) DO NOTHING;

INSERT INTO rooms (room_number, room_type_id, floor, status)
SELECT '10' || ROW_NUMBER() OVER(), rt.id, 1, 'available'
FROM generate_series(1, 5)
CROSS JOIN (SELECT id FROM room_types WHERE code = 'STD' LIMIT 1) rt
ON CONFLICT (room_number) DO NOTHING;

INSERT INTO rooms (room_number, room_type_id, floor, status)
SELECT '20' || ROW_NUMBER() OVER(), rt.id, 2, 'available'
FROM generate_series(1, 5)
CROSS JOIN (SELECT id FROM room_types WHERE code = 'DLX' LIMIT 1) rt
ON CONFLICT (room_number) DO NOTHING;

INSERT INTO rooms (room_number, room_type_id, floor, status)
SELECT '30' || ROW_NUMBER() OVER(), rt.id, 3, 'available'
FROM generate_series(1, 3)
CROSS JOIN (SELECT id FROM room_types WHERE code = 'STE' LIMIT 1) rt
ON CONFLICT (room_number) DO NOTHING;

INSERT INTO rooms (room_number, room_type_id, floor, status)
SELECT '40' || ROW_NUMBER() OVER(), rt.id, 4, 'available'
FROM generate_series(1, 3)
CROSS JOIN (SELECT id FROM room_types WHERE code = 'FAM' LIMIT 1) rt
ON CONFLICT (room_number) DO NOTHING;

INSERT INTO rate_plans (name, code, description, plan_type, adjustment_type, adjustment_value, valid_from, valid_to, is_active, priority)
VALUES
    ('Complimentary Rate', 'COMP', 'Complimentary rate for special guests, VIPs, and promotional purposes', 'promotional', 'override', 0.00, '2023-01-01', '2026-12-31', true, 100),
    ('Standard Rack Rate', 'RACK', 'Standard published rate for walk-in guests', 'standard', 'override', NULL, '2023-01-01', '2026-12-31', true, 50),
    ('Corporate Rate', 'CORP', 'Discounted rate for corporate clients and business travelers', 'corporate', 'percentage', -20.00, '2023-01-01', '2026-12-31', true, 60),
    ('Weekend Rate', 'WKND', 'Special rate for weekend stays (Friday-Sunday)', 'seasonal', 'percentage', 15.00, '2023-01-01', '2026-12-31', true, 55),
    ('Early Bird Rate', 'EARLY', 'Discounted rate for bookings made 30+ days in advance', 'promotional', 'percentage', -30.00, '2023-01-01', '2026-12-31', true, 70),
    ('Group Rate', 'GROUP', 'Special rate for group bookings (5+ rooms)', 'group', 'percentage', -25.00, '2023-01-01', '2026-12-31', true, 65)
ON CONFLICT (code) DO NOTHING;

UPDATE rate_plans SET
    applies_monday = false, applies_tuesday = false, applies_wednesday = false, applies_thursday = false,
    applies_friday = false, applies_saturday = true, applies_sunday = true
WHERE code = 'WKND';

UPDATE rate_plans SET min_advance_booking = 30 WHERE code = 'EARLY';
UPDATE rate_plans SET min_nights = 1 WHERE code = 'GROUP';

INSERT INTO promotions (
    slug, name, description, terms, status, promotion_kind, discount_type,
    discount_value, currency, min_nights, min_subtotal, per_guest_limit,
    is_public, is_cancellable, created_by, updated_by
)
SELECT
    'welcome-deluxe-10', 'Welcome Deluxe 10%',
    'A one-time welcome voucher for 10% off a Deluxe Room.',
    'Valid for one eligible Deluxe Room booking. One voucher per guest.',
    'published', 'voucher', 'percentage', 10.00, 'USD', 1, 0, 1,
    false, true, u.id, u.id
FROM users u
WHERE u.username = 'admin'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO promotion_room_types (promotion_id, room_type_id)
SELECT p.id, rt.id
FROM promotions p
JOIN room_types rt ON rt.code = 'DLX'
WHERE p.slug = 'welcome-deluxe-10'
ON CONFLICT DO NOTHING;

INSERT INTO room_rates (rate_plan_id, room_type_id, price, effective_from, effective_to)
SELECT rp.id, rt.id, v.price, '2023-01-01', '2026-12-31'
FROM rate_plans rp
CROSS JOIN (VALUES
    ('STD', 0.00), ('DLX', 0.00), ('STE', 0.00), ('FAM', 0.00)
) AS v(code, price)
JOIN room_types rt ON rt.code = v.code
WHERE rp.code = 'COMP' AND rt.id IS NOT NULL
ON CONFLICT (rate_plan_id, room_type_id, effective_from) DO NOTHING;

INSERT INTO room_rates (rate_plan_id, room_type_id, price, effective_from, effective_to)
SELECT rp.id, rt.id, v.price, '2023-01-01', '2026-12-31'
FROM rate_plans rp
CROSS JOIN (VALUES
    ('STD', 150.00), ('DLX', 250.00), ('STE', 450.00), ('FAM', 350.00)
) AS v(code, price)
JOIN room_types rt ON rt.code = v.code
WHERE rp.code = 'RACK' AND rt.id IS NOT NULL
ON CONFLICT (rate_plan_id, room_type_id, effective_from) DO NOTHING;

INSERT INTO room_rates (rate_plan_id, room_type_id, price, effective_from, effective_to)
SELECT rp.id, rt.id, v.price, '2023-01-01', '2026-12-31'
FROM rate_plans rp
CROSS JOIN (VALUES
    ('STD', 120.00), ('DLX', 200.00), ('STE', 360.00), ('FAM', 280.00)
) AS v(code, price)
JOIN room_types rt ON rt.code = v.code
WHERE rp.code = 'CORP' AND rt.id IS NOT NULL
ON CONFLICT (rate_plan_id, room_type_id, effective_from) DO NOTHING;

INSERT INTO room_rates (rate_plan_id, room_type_id, price, effective_from, effective_to)
SELECT rp.id, rt.id, v.price, '2023-01-01', '2026-12-31'
FROM rate_plans rp
CROSS JOIN (VALUES
    ('STD', 172.50), ('DLX', 287.50), ('STE', 517.50), ('FAM', 402.50)
) AS v(code, price)
JOIN room_types rt ON rt.code = v.code
WHERE rp.code = 'WKND' AND rt.id IS NOT NULL
ON CONFLICT (rate_plan_id, room_type_id, effective_from) DO NOTHING;

INSERT INTO room_rates (rate_plan_id, room_type_id, price, effective_from, effective_to)
SELECT rp.id, rt.id, v.price, '2023-01-01', '2026-12-31'
FROM rate_plans rp
CROSS JOIN (VALUES
    ('STD', 105.00), ('DLX', 175.00), ('STE', 315.00), ('FAM', 245.00)
) AS v(code, price)
JOIN room_types rt ON rt.code = v.code
WHERE rp.code = 'EARLY' AND rt.id IS NOT NULL
ON CONFLICT (rate_plan_id, room_type_id, effective_from) DO NOTHING;

INSERT INTO room_rates (rate_plan_id, room_type_id, price, effective_from, effective_to)
SELECT rp.id, rt.id, v.price, '2023-01-01', '2026-12-31'
FROM rate_plans rp
CROSS JOIN (VALUES
    ('STD', 112.50), ('DLX', 187.50), ('STE', 337.50), ('FAM', 262.50)
) AS v(code, price)
JOIN room_types rt ON rt.code = v.code
WHERE rp.code = 'GROUP' AND rt.id IS NOT NULL
ON CONFLICT (rate_plan_id, room_type_id, effective_from) DO NOTHING;
