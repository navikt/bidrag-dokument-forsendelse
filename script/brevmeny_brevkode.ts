// @ts-nocheck

const fs = require('fs');
const kodemapper = require('./kodemapper');
const brevkoderSomKreverInnkrevingDetaljer = [
  "BI01B22",
  "BI01S05",
  "BI01S17",
  "BI01F01",
  "BI01G50",
  "BI01G02",
  "BI01G01",
  "BI01F50",
  "BI01F02"
]
const brevmeny2 = fs.readFileSync('./brevmeny_export_5.json', 'utf8')
const brevmeny2jsonData = JSON.parse(brevmeny2)

const dokumentValgListeBySoknadGruppe = new Map<string, Set>()
const skipBrevkode = ["BI01B02", "BI01B11", "BI01E03", "BI01S25", "BI01S67", "BI01S68", "BI01S70"]

const brevkoderSomErstattesAvEttBrev = {
  "BI01S52": ["BI01S53"],
  // "BI01S54": ["BI01S55", "BI01S56", "BI01S57", "BI01S58"],
  "BI01S47": ["BI01S48", "BI01S49"],
  "BI01S27": ["BI01S28", "BI01S29", "BI01S30", "BI01S45"],
  "BI01S15": ["BI01S16"],
  "BI01S12": ["BI01S13"]
}

interface IBrevalg {
  stonad: string;
  vedtakType: string;
  saksttype: string;
  behandlingStatus: string;
  soknadGruppe: string;
  stonadType: string;
  soknadType: string[];
  soknadFra: string[];
  brevkode: string[];
  erKlageEnhet: string;
  erVedtakIkkeTilbakekreving: boolean;
  erFattetVedtak: boolean;
  erManuelBeregning: boolean;
  tittel: string;
}

const ignorer_brevkoder = ["BI01P11", "BI01S02", "BI01S10", "BI01S67", "BI01P18", "BI01X01", "BI01X02", "BI01B02", "BI01B11", "BI01E03", "BI01S25", "BI01S67", "BI01S68", "BI01S70", "BI01S61", "BI01S65", "BI01P17", "BI01S31", "BI01S32", "BI01S33", "BI01S34", "BI01S36", "BI01S63", "BI01S23"]
brevmeny2jsonData.forEach((data: Record<string, string>) => {
      const result: IBrevalg = {
        "stonad": `${data["KODE_STONAD"]?.trim()}`, // HG  -- ER XX hvis urelevant
        "sakstype": `${data["KODE_SAKSTYPE"]?.trim()}`, // UG  -- Er XX hvis urelevant
        "soknadGruppe": `${kodemapper.soknadGruppeToName(data["KODE_SOKN_GR"]?.trim())}`, // StønadType eller EngangsbeløpType
        "soknadType": [`${kodemapper.soknadTypeToName(data["SOKN_TYPE"]?.trim())}`],
        "soknadFra": [`${kodemapper.soknadFraToName(data["SOKN_FRA_KODE"]?.trim())}`],
        brevkode: [data["KODE_BREV"]?.trim()],
        erKlageEnhet: data["KONTOR"]?.trim() == "FTK",
        erVedtakIkkeTilbakekreving: data["KODE_STONAD"]?.trim() == "IT",
        erFattetVedtak: data["FATTET_VEDTAK"]?.trim() == "J",
        erManuelBeregning: data["FATTET_VEDTAK_GJENNOM_BBM"]?.trim() === "N",
        tittel: data["BESKR_BREV"]?.trim()?.replace("", "")
      }
      if (ignorer_brevkoder.includes(result.brevkode[0])) return
      const soknadGruppe = result.soknadGruppe
      result.stonadType = kodemapper.soknadGruppeToStonadType(result.soknadGruppe)
      result.engangsbelopType = kodemapper.soknadGruppeToEngangsbelopType(result.soknadGruppe)
      result.vedtakType = [kodemapper.soknadTypeToVedtakType(result.soknadType[0])]

      const existing = dokumentValgListeBySoknadGruppe.get(soknadGruppe)
      if (existing) {
        existing.add(result)
      } else {
        dokumentValgListeBySoknadGruppe.set(soknadGruppe, new Set([result]))
      }
    }
)
const isEqualContains = (obj1, obj2, key) => {

  const objLongest = obj1[key].length > obj2[key].length ? obj1[key] : obj2[key]
  const objShortest = obj1[key].length > obj2[key].length ? obj2[key] : obj1[key]
  return objShortest.every((value) => objLongest.includes(value))
}

const isEqual = (obj1, obj2, key) => {

  const obj1Value = obj1[key]
  const obj2Value = obj2[key]
  return obj1Value.length == obj2Value.length && obj1Value.every((value) => obj2Value.includes(value))
}
const isEqualBySoknadFra = (obj1, obj2, byContains) => byContains == false ? isEqual(obj1, obj2, "soknadFra") : isEqualContains(obj1, obj2, "soknadFra")
const isEqualBySoknadGruppe = (obj1, obj2) => isEqual(obj1, obj2, "soknadGruppe")
const isEqualByBrevkode = (obj1, obj2, byContains) => byContains == false ? isEqual(obj1, obj2, "brevkode") : isEqualContains(obj1, obj2, "brevkode")
const isEqualByVedtakType = (obj1, obj2, byContains) => byContains == false ? isEqual(obj1, obj2, "soknadType") : isEqualContains(obj1, obj2, "soknadType")


const dokumentValgKtBySoknadGruppe = Array.from(dokumentValgListeBySoknadGruppe).reduce((existingVedtakType, [soknadGruppe, value]) => {

  const values = Array.from(value)

  const mergedValuesBrevkode = values.reduce((prevValues, currObj) => {
    const existingObject = prevValues
    .find((filterObj) => isEqualBySoknadFra(filterObj, currObj) && isEqualByVedtakType(filterObj, currObj) && filterObj.erFattetVedtak == currObj.erFattetVedtak && filterObj.erManuelBeregning == currObj.erManuelBeregning && filterObj.erKlageEnhet == currObj.erKlageEnhet && filterObj.erVedtakIkkeTilbakekreving == currObj.erVedtakIkkeTilbakekreving)

    if (existingObject) {
      existingObject.brevkode = Array.from(new Set([...existingObject.brevkode, ...currObj.brevkode].sort()))
    } else {
      prevValues.push(currObj)
    }
    return prevValues
  }, [])


  const mergedValues2 = mergedValuesBrevkode.reduce((prevValues, currObj) => {
    const existingObject = prevValues
    .find((filterObj) => isEqualByVedtakType(filterObj, currObj) && isEqualBySoknadFra(filterObj, currObj) && isEqualByBrevkode(filterObj, currObj) && filterObj.erFattetVedtak == currObj.erFattetVedtak && filterObj.erManuelBeregning == currObj.erManuelBeregning && filterObj.erVedtakIkkeTilbakekreving == currObj.erVedtakIkkeTilbakekreving)
    if (currObj.erVedtakIkkeTilbakekreving && currObj.vedtakType == "KLAGE") {
      prevValues.push(currObj)
      return prevValues
    }
    if (existingObject) {
      delete existingObject.erKlageEnhet
    } else {
      prevValues.push(currObj)
    }
    return prevValues
  }, [])

  // const mergedValues3 = mergedValues2.reduce((prevValues, currObj) => {
  //   const existingObject = prevValues
  //   .find((filterObj) => isEqualByVedtakType(filterObj, currObj) && isEqualBySoknadFra(filterObj, currObj) && isEqualByBrevkode(filterObj, currObj) && filterObj.erKlageEnhet == currObj.erKlageEnhet && filterObj.erManuelBeregning == currObj.erManuelBeregning)
  //
  //   if (existingObject) {
  //     delete existingObject.erFattetVedtak
  //   } else {
  //     prevValues.push(currObj)
  //   }
  //   return prevValues
  // }, [])
  const mergedValues4 = mergedValues2.reduce((prevValues, currObj) => {
    const existingObject = prevValues
    .find((filterObj) => isEqualByVedtakType(filterObj, currObj, false) && isEqualBySoknadFra(filterObj, currObj, false) && isEqualByBrevkode(filterObj, currObj, false) && filterObj.erKlageEnhet == currObj.erKlageEnhet && filterObj.erFattetVedtak == currObj.erFattetVedtak && filterObj.erVedtakIkkeTilbakekreving == currObj.erVedtakIkkeTilbakekreving)

    if (existingObject) {
      delete existingObject.erManuelBeregning
    } else {
      prevValues.push(currObj)
    }
    return prevValues
  }, [])

  const mergedValuesVedtakType1 = mergedValues4.reduce((prevValues, currObj) => {
    const existingObject = prevValues
    .find((filterObj) => isEqualBySoknadFra(filterObj, currObj, false) && isEqualByBrevkode(filterObj, currObj, false) && filterObj.erKlageEnhet === currObj.erKlageEnhet && filterObj.erFattetVedtak == currObj.erFattetVedtak && filterObj.erVedtakIkkeTilbakekreving == currObj.erVedtakIkkeTilbakekreving)

    if (currObj.erVedtakIkkeTilbakekreving && currObj.vedtakType == "KLAGE") {
      prevValues.push(currObj)
      return prevValues
    }
    if (existingObject) {
      existingObject.soknadType = Array.from(new Set([...existingObject.soknadType, ...currObj.soknadType].sort()))
      existingObject.vedtakType = Array.from(new Set([...existingObject.vedtakType, ...currObj.vedtakType].sort()))
    } else {
      prevValues.push(currObj)
    }
    return prevValues
  }, [])

  const mergedValuesVedtakType2 = mergedValuesVedtakType1.reduce((prevValues, currObj) => {
    const existingObject = prevValues
    .find((filterObj) => isEqualByVedtakType(filterObj, currObj, false) && isEqualByBrevkode(filterObj, currObj, false) && filterObj.erKlageEnhet === currObj.erKlageEnhet && filterObj.erFattetVedtak == currObj.erFattetVedtak && filterObj.erVedtakIkkeTilbakekreving == currObj.erVedtakIkkeTilbakekreving)
    if (currObj.erVedtakIkkeTilbakekreving && currObj.vedtakType == "KLAGE") {
      prevValues.push(currObj)
      return prevValues
    }
    if (existingObject) {
      existingObject.soknadFra = Array.from(new Set([...existingObject.soknadFra, ...currObj.soknadFra].sort()))
    } else {
      prevValues.push(currObj)
    }
    return prevValues
  }, [])


  const behandlingType = mergedValuesVedtakType2[0].stonadType ?? mergedValuesVedtakType2[0].engangsbelopType ?? mergedValuesVedtakType2[0].soknadGruppe
  existingVedtakType[behandlingType] = mergedValuesVedtakType2.map((v) => ({
    ...v,
    // soknadType: v.soknadType[0],
    // soknadFra: v.soknadFra[0],
    behandlingStatus: kodemapper.toBehandlingStatusKode(v.erFattetVedtak, v.erManuelBeregning),
    forvaltning: kodemapper.toForvaltningKode(v.erKlageEnhet),
    brevkoder: v.brevkode,
    vedtakType: v.vedtakType.filter((vt) => !["KORRIGERING", "EGET_TILTAK"].includes(vt))
  })).map((v) => {
    delete v.tittel
    delete v.stonad
    delete v.sakstype
    delete v.soknadGruppe
    delete v.brevkode
    delete v.erManuelBeregning
    delete v.erFattetVedtak
    delete v.erKlageEnhet
    // delete v.soknadType
    if (v.engangsbelopType == null) delete v.engangsbelopType
    if (v.stonadType == null) delete v.stonadType
    return v
  }).map((v) => ({
    ...v,
    brevkoder: settSammenBrevkoder(v.brevkoder)
  }))

  return existingVedtakType;
}, {})


function manueltFjernetBrevkoder(mapValues: Map<string, IBrevalg[]>): Map<string, IBrevalg[]> {
  // 20.09.2023 - Etter fattet vedtak om opphør av bidrag så kom det bare forslag om varselbrev istedenfor vedtaksbrev. Dette skal fikse feilen
  mapValues["BIDRAG"].push({
    "soknadType": [
      "OPPHOR"
    ],
    "soknadFra": [
      "NAV_BIDRAG"
    ],
    "erVedtakIkkeTilbakekreving": false,
    "stonadType": "BIDRAG",
    "vedtakType": [
      "OPPHØR"
    ],
    "behandlingStatus": "FATTET_MANUELT",
    "forvaltning": "BIDRAG",
    "brevkoder": [
      "BI01G01",
      "BI01G02"
    ]
  })
  mapValues["BIDRAG"] = mapValues["BIDRAG"].reduce((previousValue, currentValue) => {
    if (currentValue.vedtakType.includes("OPPHØR") && currentValue.brevkoder.length == 1 && currentValue.brevkoder.includes("BI01S07") && currentValue.soknadFra.includes("NAV_BIDRAG")) {
      currentValue.vedtakType = currentValue.vedtakType.filter((v) => v != "OPPHØR")
      currentValue.soknadType = currentValue.soknadType.filter((v) => v != "OPPHOR")
    }
    // Begrenset revurdering av bidrag skal ikke vise brev S06 men S22 og S23
    if (currentValue.soknadType.includes("BEGRENSET_REVURDERING") && currentValue.behandlingStatus == "IKKE_FATTET" && currentValue.brevkoder.length == 1 && currentValue.brevkoder.includes("BI01S06") && currentValue.soknadFra.includes("NAV_BIDRAG")) {
      currentValue.vedtakType = currentValue.vedtakType.filter((v) => v != "REVURDERING")
      currentValue.soknadType = currentValue.soknadType.filter((v) => v != "BEGRENSET_REVURDERING")
    }

    if (currentValue.soknadType.includes("BEGRENSET_REVURDERING") && currentValue.behandlingStatus == "IKKE_FATTET") {
      currentValue.brevkoder = [...new Set(["BI01S07", "BI01S22", ...currentValue.brevkoder])]
    }
    previousValue.push(currentValue)
    return previousValue
  }, [])

  // Legg til brev S21 og S22 på alle varsler for klage
  Object.keys(mapValues).forEach((key) => {
    mapValues[key] = mapValues[key].reduce((previousValue, currentValue) => {
      if (currentValue.soknadType.includes("KLAGE") && currentValue.behandlingStatus == "IKKE_FATTET" && currentValue.forvaltning == "BIDRAG" && currentValue.brevkoder.includes("BI01S64")) {
        currentValue.brevkoder = [...new Set(["BI01S20", "BI01S21", ...currentValue.brevkoder])]
      }
      previousValue.push(currentValue)
      return previousValue
    }, [])
  })

  return mapValues
}

function settSammenBrevkoder(brevkoder: string[]): string[] {
  let brevkoderSattSammen = [...brevkoder]
  Object.keys(brevkoderSomErstattesAvEttBrev).forEach((brevkodeSomErstattesMed) => {
    const fjernBrevkoder = brevkoderSomErstattesAvEttBrev[brevkodeSomErstattesMed]
    const inneholderBrevkodeSomSkalErstattes = fjernBrevkoder.some((b) => brevkoder.includes(b))
    if (inneholderBrevkodeSomSkalErstattes) {
      brevkoderSattSammen = brevkoderSattSammen.filter((b) => !fjernBrevkoder.includes(b))
      brevkoderSattSammen.push(brevkodeSomErstattesMed)
    }
  })
  return [...new Set(brevkoderSattSammen)]
}


fs.writeFileSync('../src/main/resources/files/dokument_valg.json', JSON.stringify(manueltFjernetBrevkoder(dokumentValgKtBySoknadGruppe), null, 2));