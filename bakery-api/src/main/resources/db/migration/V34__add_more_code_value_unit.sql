INSERT INTO code_value (group_key, code, name, sort_order, status, approval_status)
VALUES
    ('UNIT', 'BO',    'Bộ',   9, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'BINH',     'Bình',       10, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'CAY',     'Cây',        11, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'ONG',    'Ống',    12, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'CHAI',   'Chai',        13, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'QUA',   'Quả',        14, 'ACTIVE', 'APPROVED'),
    ('UNIT', 'HOP_NHO',   'Hộp Nhỏ',        15, 'ACTIVE', 'APPROVED')
ON CONFLICT (group_key, code) DO NOTHING;
