CREATE TABLE free_account_numbers
(
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY UNIQUE,
    type VARCHAR(20) NOT NULL,
    account_number VARCHAR(20) NOT NULL
);