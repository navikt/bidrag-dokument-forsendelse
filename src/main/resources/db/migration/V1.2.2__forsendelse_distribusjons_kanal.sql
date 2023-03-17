alter table forsendelse
    add column if not exists distribusjon_kanal text;

update forsendelse
set distribusjon_kanal = 'LOKAL_UTSKRIFT'
where status = 'DISTRIBUERT_LOKALT';