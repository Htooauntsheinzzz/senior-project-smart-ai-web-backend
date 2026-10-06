ALTER TABLE app_users
ADD CONSTRAINT fk_app_users_department
FOREIGN KEY (department_id)
REFERENCES departments(id);
