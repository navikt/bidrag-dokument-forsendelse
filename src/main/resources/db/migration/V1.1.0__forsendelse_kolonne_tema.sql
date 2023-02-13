alter table forsendelse
    add column if not exists tema text default 'BID';
alter table forsendelse
    add column if not exists batch_id text;