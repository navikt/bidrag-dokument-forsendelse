#!/bin/bash
kubectx nais-dev

deployment="deployment/bidrag-dokument-forsendelse"
[ "$1" == "q1" ] && deployment="deployment/bidrag-dokument-forsendelse-feature"
echo "Henter miljøparametere fra deployment: $deployment"
kubectl exec --tty $deployment -- printenv | grep -E 'AZURE_|_URL|SCOPE|UNLEASH' | grep -E 'AZURE_|_URL|SCOPE|UNLEASH' | grep -v -e 'BIDRAG_BEHANDLING_URL' -e 'BIDRAG_TILGANGSKONTROLL_URL' -e 'KODEVERK_URL' -e 'BIDRAG_DOKUMENT_BESTILLING_URL' -e 'BIDRAG_VEDTAK_URL' > src/test/resources/application-lokal-nais-secrets.properties