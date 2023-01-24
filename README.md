# Bidrag-dokument-forsendelse
Template repo for å opprette ny Spring applikasjon for Bidrag

[![continuous integration](https://github.com/navikt/bidrag-dokument-forsendelse/actions/workflows/ci.yaml/badge.svg)](https://github.com/navikt/bidrag-dialog/actions/workflows/ci.yaml)
[![release bidrag-dokument-forsendelse](https://github.com/navikt/bidrag-dokument-forsendelse/actions/workflows/release.yaml/badge.svg)](https://github.com/navikt/bidrag-dialog/actions/workflows/release.yaml)

## Beskrivelse

Legg til Github secret `NAIS_DEPLOY_APIKEY` hvor secret hentes fra [Api key](https://deploy.nais.io/apikeys)

## Kjøre applikasjonen lokalt

Start opp applikasjonen ved å kjøre [BidragTemplateLocal.kt](src/test/kotlin/no/nav/bidrag/template/BidragTemplateLocal.kt).
Dette starter applikasjonen med profil `local` og henter miljøvariabler for Q1 miljøet fra filen [application-local.yaml](src/test/resources/application-local.yaml).

Her mangler det noen miljøvariabler som ikke bør committes til Git (Miljøvariabler for passord/secret osv).<br/>
Når du starter applikasjon må derfor følgende miljøvariabl(er) settes:
```bash
-DAZURE_APP_CLIENT_SECRET=<secret>
-DAZURE_APP_CLIENT_ID=<id>
```
Disse kan hentes ved å kjøre kan hentes ved å kjøre 
```bash
kubectl exec --tty deployment/bidrag-dialog-feature -- printenv | grep -e AZURE_APP_CLIENT_ID -e AZURE_APP_CLIENT_SECRET
```

### Live reload
Med `spring-boot-devtools` har Spring støtte for live-reload av applikasjon. Dette betyr i praksis at Spring vil automatisk restarte applikasjonen når en fil endres. Du vil derfor slippe å restarte applikasjonen hver gang du gjør endringer. Dette er forklart i [dokumentasjonen](https://docs.spring.io/spring-boot/docs/1.5.16.RELEASE/reference/html/using-boot-devtools.html#using-boot-devtools-restart).
For at dette skal fungere må det gjøres noe endringer i Intellij instillingene slik at Intellij automatisk re-bygger filene som er endret:

* Gå til `Preference -> Compiler -> check "Build project automatically"`
* Gå til `Preference -> Advanced settings -> check "Allow auto-make to start even if developed application is currently running"`

#### Kjøre lokalt mot sky
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
kubectl config use dev-fss
```
Deretter kjør følgende kommando for å importere secrets. Viktig at filen som opprettes ikke committes til git

```bash
kubectl exec --tty deployment/bidrag-dokument-forsendelse-feature printenv | grep -E 'AZURE_|_URL|SCOPE' > src/test/resources/application-lokal-nais-secrets.properties
```

Deretter kan tokenet brukes til å logge inn på swagger-ui http://localhost:8999/swagger-ui.html


### Kafka

Bruk `kcat` til å sende meldinger til kafka topic. Feks

````bash
kcat -b 0.0.0.0:9092 -t bidrag-dokument -P -K:
````
og lim inn eks:
```bash
BIF_2121212121:{"dokumentreferanse":"BIF_1000000007","journalpostId":null,"forsendelseId":null,"sporingId":"1853dd066d1-brevkvittering_3884646513","arkivSystem":"MIDLERTIDLIG_BREVLAGER","status":"UNDER_PRODUKSJON","hendelseType":"ENDRING"}
```
og deretter trykk Ctrl+D. Da vil meldingen bli sendt til topic bidrag-journalpost

For oppgave
````bash
kcat -b 0.0.0.0:9092 -t bidrag-opprettet -P
````
og lim inn eks:
```bash
{"id": 351382364, "tildeltEnhetsnr": "4806", "opprettetAvEnhetsnr": "4806",  "journalpostId": "573782796", "aktoerId": "2578652659686", "beskrivelse": "Test kopier dokumenter til Bidrag", "tema": "BID", "oppgavetype": "VUR", "versjon": 1, "opprettetAv": "srvbisys", "prioritet": "HOY", "status": "OPPRETTET"}
```


#### Legg til srvbdforsendelse brukernavn/passord på kubernetes
Bidrag-dokument-forsendelse bruker servicebruker for å kunne utføre tilgangskontroll. For å kjøre bidrag-ui på NAIS må en redis secret bli opprettet. Redis secret brukes for sikker kommunikasjon med redis instansen.
Kjør følgende kommando for å opprette secret på namespace bidrag

``
kubectl create secret generic bidrag-dokument-forsendelse-secrets --from-literal=SRV_USERNAME=srvbdforsendelse -n=bidrag
``