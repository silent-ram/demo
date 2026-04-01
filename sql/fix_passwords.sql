-- 修复用户密码哈希
USE fault_warning_user;

-- 更新 admin 用户的密码哈希（密码：admin123）
UPDATE t_user SET password = '$2a$10$ml4zEGX1xpO1OEvMhFP3S.vKy15zshAqySbTnzahKRvuGlSuxvaMa' WHERE username = 'admin';

-- 更新 operator 用户的密码哈希（密码：operator123）
UPDATE t_user SET password = '$2a$10$k0i8nyAZFbhz.X08BGZ/outq8HNJucilC0vPmoGVxky2yck5t12ui' WHERE username = 'operator';

-- 验证更新
SELECT id, username, role, created_at FROM t_user;

SELECT 'Password hashes updated successfully!' AS status;
-- 测试账号：
-- admin / admin123
-- operator / operator123