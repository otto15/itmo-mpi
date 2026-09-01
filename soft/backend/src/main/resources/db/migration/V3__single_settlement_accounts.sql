alter table settlement_membership
    add constraint settlement_membership_one_settlement_per_user unique (user_id);

create unique index user_account_username_ci_idx on user_account (lower(username));
