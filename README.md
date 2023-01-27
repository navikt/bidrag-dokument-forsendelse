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
kubectl exec --tty deployment/bidrag-dokument-forsendelse-feature printenv | grep -E 'AZURE_|_URL|SCOPE' > src/test/resources/application-lokal-nais-secrets.properties
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
kcat -b 0.0.0.0:9092 -t bidrag-dokument -P -K:
````

og lim inn eks:

```bash
BIF_2121212121:{"dokumentreferanse":"BIF_1000000007","journalpostId":null,"forsendelseId":null,"sporingId":"1853dd066d1-brevkvittering_3884646513","arkivSystem":"MIDLERTIDLIG_BREVLAGER","status":"UNDER_PRODUKSJON","hendelseType":"ENDRING"}
```

og deretter trykk Ctrl+D. Da vil meldingen bli sendt til topic bidrag-dokument