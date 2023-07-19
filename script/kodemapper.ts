export function toBehandlingStatusKode(erfattetVedtak: boolean, erManuelBeregning: boolean) {
  if (erfattetVedtak == null) {
    return "IKKE_RELEVANT"
  }
  if (erfattetVedtak == true) {
    return erManuelBeregning == null ? "FATTET" : erManuelBeregning == true ? "FATTET_MANUELT" : "FATTET_BEREGNET"
  }
  return "IKKE_FATTET"
}

export function toForvaltningKode(erKlageEnhet: boolean) {
  if (erKlageEnhet == null) {
    return "BEGGE"
  }
  return erKlageEnhet ? "KLAGE_ANKE" : "BIDRAG"
}

export function soknadFraToName(kode: string) {
  switch (kode) {
    case "AS":
      return "BM_I_ANNEN_SAK"
    case "BB":
      return "BARN_18_AAR"
    case "ET":
      return "NAV_BIDRAG"
    case "FN":
      return "FYLKESNEMDA"
    case "IN":
      return "NAV_INTERNASJONALT"
    case "KU":
      return "KOMMUNE"
    case "KV":
      return "KONVERTERING"
    case "MO":
      return "BIDRAGSMOTTAKER"
    case "NM":
      return "NORSKE_MYNDIGHET"
    case "PL":
      return "BIDRAGSPLIKTIG"
    case "UM":
      return "UTENLANDSKE_MYNDIGHET"
    case "VE":
      return "VERGE"
    case "FK":
      return "KLAGE_ANKE"
    case "TI":
      return "TRYGDEETATEN_INNKREVING"
    default:
      return kode
  }
}

export function soknadTypeToName(kode: string) {
  switch (kode) {
    case "EN":
      return "ENDRING"
    case "ET":
      return "EGET_TILTAK"
    case "FA":
      return "SOKNAD"
    case "IG":
      return "INNKREVINGSGRUNNL"
    case "IR":
      return "INDEKSREG"
    case "KB":
      return "KLAGE_BEGR_SATS"
    case "KL":
      return "KLAGE"
    case "KM":
      return "FOLGER_KLAGE"
    case "KV":
      return "KONVERTERING"
    case "OB":
      return "OMGJORING_BEGR_SATS"
    case "OF":
      return "OPPJUST_FORSK"
    case "OH":
      return "OPPHOR"
    case "OM":
      return "OMGJORING"
    case "PA":
      return "PRIVAT_AVTALE"
    case "RB":
      return "BEGR_REVURD"
    case "RF":
      return "REVURDERING"
    case "KR":
      return "KORRIGERING"
    default:
      return kode
  }
}

export function soknadGruppeToName(kode: string) {
  switch (kode) {
    case "AV":
      return "AVSKRIVNING"
    case "EB":
      return "EKTEFELLEBIDRAG"
    case "18":
      return "BIDRAG_18_AR"
    case "BI":
      return "BIDRAG"
    case "BT":
      return "BIDRAG_TILLEGGSBIDRAG"
    case "DO":
      return "DIREKTE_OPPGJOR"
    case "EG":
      return "ETTERGIVELSE"
    case "ER":
      return "ERSTATNING"
    case "FA":
      return "FARSKAP"
    case "FO":
      return "FORSKUDD"
    case "GB":
      return "GEBYR"
    case "IK":
      return "INNKREVING"
    case "MR":
      return "MOTREGNING"
    case "RB":
      return "REFUSJON_BIDRAG"
    case "SO":
      return "SAKSOMKOSTNINGER"
    case "ST":
      return "SARTILSKUDD"
    case "T1":
      return "BIDRAG_18_AR_TILLEGGSBBI"
    case "TB":
      return "TILLEGGSBIDRAG"
    case "TE":
      return "TILBAKEKR_ETTERGIVELSE"
    case "TK":
      return "TILBAKEKREVING"
    case "OB":
      return "OPPFOSTRINGSBIDRAG"
    case "MO":
      return "MORSKAP"
    case "FB":
      return "KUNNSKAP_BIOLOGISK_FAR"
    case "BF":
      return "BARNEBORTFORING"
    case "KV":
      return "KONVERTERT_VERDI"
    case "RK":
      return "REISEKOSTNADER"
    default:
      return kode
  }
}

export function soknadGruppeToEngangsbelopType(kode: string) {
  if (kode == "SARTILSKUDD") {
    return "SAERTILSKUDD"
  } else if (kode == "GEBYR") {
    return "GEBYR_SKYLDNER" // GEBYR_SKYLDNER hvis BP eller GEBYR_MOTTAKER
  } else if ("ETTERGIVELSE" == kode) {
    return "ETTERGIVELSE"
  } else if ("TILBAKEKREVING" == kode) {
    return "TILBAKEKREVING"
  } else if ("TILBAKEKR_ETTERGIVELSE" == kode) {
    return "ETTERGIVELSE_TILBAKEKREVING"
  }
  return null
}

export function soknadGruppeToStonadType(kode: string) {
  if (kode == "FORSKUDD") {
    return "FORSKUDD"
  } else if (["BIDRAG", "BIDRAG_TILLEGGSBIDRAG", "TILLEGGSBIDRAG"].includes(kode)) {
    return "BIDRAG"
  } else if (["BIDRAG_18_AR", "BIDRAG_18_AR_TILLEGGSBBI"].includes(kode)) {
    return "BIDRAG18AAR"
  } else if ("EKTEFELLEBIDRAG" == kode) {
    return "EKTEFELLEBIDRAG"
  } else if ("MOTREGNING" == kode) {
    return "MOTREGNING"
  } else if ("OPPFOSTRINGSBIDRAG" == kode) {
    return "OPPFOSTRINGSBIDRAG"
  }
  return null
}

export function soknadTypeToVedtakType(kode: string, soknadFra: string) {
  if (kode == "SOKNAD") {
    return "FASTSETTELSE"
  } else if (["INNKREVINGSGRUNNL", "PRIVAT_AVTALE"].includes(kode)) {
    return "INNKREVING"
  } else if ("INDEKSREG" == kode) {
    return "INDEKSREGULERING"
  } else if (["KLAGE_BEGR_SATS", "KLAGE", "FOLGER_KLAGE"].includes(kode)) {
    return "KLAGE"
  } else if (["BEGR_REVURD", "REVURDERING", "EGET_TILTAK"].includes(kode)) {
    return "REVURDERING"
  } else if ("OPPHOR" == kode) {
    return "OPPHØR"
  } else if ("OPPJUST_FORSK" == kode && soknadFra == "BIDRAGSENHET") {
    return "ALDERSJUSTERING"
  }
  return "ENDRING"
}