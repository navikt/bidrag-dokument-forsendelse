alter table forsendelse
    add column if not exists metadata hstore;

create index forsendelse_meta_navno_redist_idx on forsendelse using btree ((metadata -> 'sjekket_navno_redistribusjon_til_sentral_print'))

