alter table forsendelse
    drop column if exists tema;
alter table forsendelse
    add column if not exists tema text not null default 'BID';
alter table forsendelse
    add column if not exists batch_id text;