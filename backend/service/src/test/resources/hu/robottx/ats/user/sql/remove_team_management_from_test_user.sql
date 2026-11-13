-- test-user stays an employee everywhere, but without permission to manage any team
UPDATE employees SET role = 'HR_MANAGER' WHERE user_id = 'test-user';
