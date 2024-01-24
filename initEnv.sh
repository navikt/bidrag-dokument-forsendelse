kubectx dev-gcp
kubectl exec --tty deployment/bidrag-dokument-forsendelse printenv | grep -E 'AZURE_|_URL|SCOPE|UNLEASH' | grep -v -e 'BIDRAG_BEHANDLING_URL' -e 'BIDRAG_TILGANGSKONTROLL_URL' -e 'BIDRAG_DOKUMENT_BESTILLING_URL' -e 'BIDRAG_VEDTAK_URL' > src/test/resources/application-lokal-nais-secrets.properties
