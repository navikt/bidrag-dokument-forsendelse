# Bidrag-dokument-forsendelse

[![continuous integration](https://github.com/navikt/bidrag-dokument-forsendelse/actions/workflows/ci.yaml/badge.svg)](https://github.com/navikt/bidrag-dokument-forsendelse/actions/workflows/ci.yaml)
[![release bidrag-dokument-forsendelse](https://github.com/navikt/bidrag-dokument-forsendelse/actions/workflows/release.yaml/badge.svg)](https://github.com/navikt/bidrag-dokument-forsendelse/actions/workflows/release.yaml)

## Beskrivelse

Forsendelse er et digitalt brev som består av en eller flere dokumenter. Bidrag dokument forsendelse
er applikasjon som brukes for å opprette og oppdatere forsendelsen.

Forsendelse er mellomlagring av en utgående journalpost hvor dokumenter kan legges til eller
slettes. Nye dokumenter bestilles basert på dokumentmal ved at forsendelse bestiller dokumentet ved
å kalle bidrag-dokument-bestilling. I tillegg skal maskerte/redigerte dokumenter lagres i
forsendelsen.
Når forsendelsen er klar for distribusjon kan distribusjon av forsendelsen bestilles som arkiverer
forsendelsen i Joark og distribusjon bestilles gjennom sentral distribusjon.

## Dokumentvalg

Dokumentvalg bruker [dokument_valg.json](src/main/resources/files/dokument_valg.json) for å finne ut hvilken brevkoder som skal vises for type
vedtak/behandling,
[dokument_valg.json](src/main/resources/files/dokument_valg.json) inneholder json med map fra behandlingtype (stonadType eller engangsbelopType) til
brevkoder.
Denne filen er generert basert på SQL eksport fra Bisys databasen som vist under.

[brevmeny_brevkode.ts](script/brevmeny_brevkode.ts) leser og konverterer SQL eksport til dokument_valg.json fil som igjen brukes av forsendelse koden.

Kjør og eksporter følgende SQL skript til json fil før kjøring av skript

```sql

--- Bisys brevmeny export
select B.TYPE_MOTTAKER,
       BV.KODE_STONAD,
       BV.KODE_SAKSTYPE,
       HGUG.KODE_SOKN_GR,
       HGUG.SOKN_FRA_KODE,
       HGUG.SOKN_TYPE,
       BIM.KODE_BREV,
       B.BESKR_BREV,
       BV.KODE_KATEGORI     as FATTET_VEDTAK_GJENNOM_BBM,
       BV.TYPE_BESLUTNING   as FATTET_VEDTAK,
       BV.KODE_BESLUT_NIVAA as KONTOR,
       BM.BESKR_BREVMENY    as PREFIKS
from BR462P.T_BREVVALG BV
         inner join BR462P.T_BREV_I_MENY BIM on BIM.KODE_BREVMENY = BV.KODE_BREVMENY
         inner join BR462P.T_BREV B on B.KODE_BREV = BIM.KODE_BREV
         inner join BR462P.T_BREVMENY BM on BM.KODE_BREVMENY = BV.KODE_BREVMENY
         inner join BR462P.T_UG_SAKSTYPE UGS on UGS.KODE_SAKSTYPE = BV.KODE_SAKSTYPE
         inner join BI464P.T_KODE_HG_UG HGUG on ((HGUG.UG = UGS.KODE_UG and HGUG.HG = BV.KODE_STONAD) or (BV.KODE_STONAD = 'XX' and HGUG.HG = ' ') or
                                                 (BV.KODE_STONAD = 'IT' and HGUG.UG = UGS.KODE_UG) or (HGUG.UG is null and HGUG.HG = BV.KODE_STONAD))
;
```

Kjør deretter [brevmeny_brevkode.ts](script/brevmeny_brevkode.ts) med følgende kommando

```bash
cd script && yarn start
```

#### Kjøre lokalt mot sky

Start lokal postgres database og kafka ved å kjøre

```bash
docker-compose up -d
```

For å kunne kjøre lokalt mot sky må du gjøre følgende

Åpne terminal på root mappen til `bidrag-dokument-forsendelse`
Konfigurer kubectl til å gå mot kluster `dev-gcp`

```bash
# Sett cluster til dev-fss
kubectx dev-gcp
# Sett namespace til bidrag
kubens bidrag 

# -- Eller hvis du ikke har kubectx/kubens installert 
# (da må -n=bidrag legges til etter exec i neste kommando)
kubectl config use dev-fcp
```

Deretter kjør følgende kommando for å importere secrets. Viktig at filen som opprettes ikke
committes til git

```bash
kubectl exec --tty deployment/bidrag-dokument-forsendelse printenv | grep -E 'AZURE_|_URL|SCOPE' | grep -v -e 'BIDRAG_BEHANDLING_URL' -e 'BIDRAG_TILGANGSKONTROLL_URL' -e 'BIDRAG_DOKUMENT_BESTILLING_URL' -e 'BIDRAG_VEDTAK_URL' > src/test/resources/application-lokal-nais-secrets.properties
```

Start opp applikasjonen ved å
kjøre [BidragDokumentForsendelseLokalNais.kt](src/test/kotlin/no/nav/bidrag/dokument/forsendelse/BidragDokumentForsendelseLokalNais.kt).

Deretter kan tokenet brukes til å logge inn på swagger-ui http://localhost:8999/swagger-ui.html

### Live reload

Med `spring-boot-devtools` har Spring støtte for live-reload av applikasjon. Dette betyr i praksis
at Spring vil automatisk restarte applikasjonen når en fil endres. Du vil derfor slippe å restarte
applikasjonen hver gang du gjør endringer. Dette er forklart
i [dokumentasjonen](https://docs.spring.io/spring-boot/docs/1.5.16.RELEASE/reference/html/using-boot-devtools.html#using-boot-devtools-restart).
For at dette skal fungere må det gjøres noe endringer i Intellij instillingene slik at Intellij
automatisk re-bygger filene som er endret:

* Gå til `Preference -> Compiler -> check "Build project automatically"`
* Gå
  til `Preference -> Advanced settings -> check "Allow auto-make to start even if developed application is currently running"`

### Kafka

Bruk `kcat` til å sende meldinger til kafka topic. Feks

````bash
kcat -b 0.0.0.0:9092 -t bidrag.dokument -P -K:
````

og lim inn eks:

```bash
BIF100000311:{"dokumentreferanse":"BIF100000411","journalpostId":null,"forsendelseId":null,"sporingId":"1853dd066d1-brevkvittering_3884646513","arkivSystem":"MIDLERTIDLIG_BREVLAGER","status":"UNDER_REDIGERING","hendelseType":"ENDRING"}
```

og deretter trykk Ctrl+D. Da vil meldingen bli sendt til topic bidrag-dokument

### Test nais.yaml implementation

```
docker run -v $(pwd)/.nais:/nais navikt/deployment:v1 ./deploy --dry-run --print-payload --resource /nais/nais.yaml --vars /nais/feature.yaml
```

### Opprett klientside GCP bucket encryption key secret

* Opprett KMS nøkkelring i GCP
* Legg til ny symmetrisk nøkkel med navn `clientside_document_encryption`
    * Rotering og sletting policy kan settes til følgende verdier (90 dager og 60 dager)
* Gi bidrag-dokument-forsendelse tilgang til nøkkelringen. Bidrag-dokument-forsendelse service bruker kan finnes under IAM brukere
    * Gi følgende tilganger:
        * Cloud KMS CryptoKey Encrypter/Decrypter
        * Cloud KMS Viewer
* Deretter kopier `Resource name` til nøkkelen og lim det inn som miljøvariabel `GCP_DOCUMENT_CLIENTSIDE_KMS_KEY_PATH` i prod.yaml

#### Connect to aiven kafka instance

