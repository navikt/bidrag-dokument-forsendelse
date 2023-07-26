alter table forsendelse
    add column if not exists behandling_info_id bigint;

ALTER TABLE forsendelse
    DROP CONSTRAINT IF EXISTS fk_beh_info_id;
alter table forsendelse
    add CONSTRAINT fk_beh_info_id FOREIGN KEY (behandling_info_id) REFERENCES behandling_info (id) MATCH SIMPLE;