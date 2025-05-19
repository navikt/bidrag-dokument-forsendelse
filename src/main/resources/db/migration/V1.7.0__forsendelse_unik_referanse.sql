ALTER TABLE forsendelse
    ADD COLUMN IF NOT EXISTS unik_referanse TEXT;

CREATE UNIQUE INDEX idx_forsendelse_unik_referanse ON forsendelse(unik_referanse) WHERE unik_referanse IS NOT NULL and status not in ('SLETTET', 'AVBRUTT');