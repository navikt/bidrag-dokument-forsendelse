drop table if exists adresse cascade;
drop table if exists mottaker cascade;
drop table if exists forsendelse cascade;
drop table if exists dokument cascade;
drop sequence if exists dokumentreferanse_seq cascade;
drop function if exists oppdater_endret_tidspunkt();