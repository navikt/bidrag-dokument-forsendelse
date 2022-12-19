# Bidrag-template-spring
Template repo for å opprette ny Spring applikasjon for Bidrag

[![continuous integration](https://github.com/navikt/bidrag-template-spring/actions/workflows/ci.yaml/badge.svg)](https://github.com/navikt/bidrag-dialog/actions/workflows/ci.yaml)
[![release bidrag-template-spring](https://github.com/navikt/bidrag-template-spring/actions/workflows/release.yaml/badge.svg)](https://github.com/navikt/bidrag-dialog/actions/workflows/release.yaml)

## Beskrivelse

Erstatt alt som har postfix `-template-spring` med din applikasjonsnavn

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