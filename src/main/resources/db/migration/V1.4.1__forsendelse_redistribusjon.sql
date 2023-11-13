alter table forsendelse
    add column if not exists bestilt_ny_distribusjon boolean default false;
comment on column forsendelse.bestilt_ny_distribusjon is 'Hvis forsendelse er distribuert via Nav.no og ikke er åpnet etter 40 timer så vil distribusjon bestilles på nytt via sentral print'