CREATE OR REPLACE FUNCTION oppdater_endret_tidspunkt()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.endret_tidspunkt = now();
    RETURN NEW;
END;
$$ language 'plpgsql';