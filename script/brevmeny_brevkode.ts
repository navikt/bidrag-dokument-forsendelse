// @ts-nocheck

const fs = require('fs');
const kodemapper = require('./kodemapper');

const brevmeny2 = fs.readFileSync('./brevmeny_export_5.json', 'utf8')
const brevmeny2jsonData = JSON.parse(brevmeny2)

const dokumentValgListeBySoknadGruppe = new Map<string, Set>()
const skipBrevkode = ["BI01B02", "BI01B11", "BI01E03", "BI01S25", "BI01S67", "BI01S68", "BI01S70"]

const ignorer_brevkoder = ["BI01P11", "BI01S02", "BI01S10", "BI01S67", "BI01P18", "BI01X01", "BI01X02", "BI01B02", "BI01B11", "BI01E03", "BI01S25", "BI01S67", "BI01S68", "BI01S70", "BI01S61", "BI01S65 "]
brevmeny2jsonData.forEach((data: Record<string, string>) => {
      const result = {
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
    .find((filterObj) => isEqualByVedtakType(filterObj, currObj) && isEqualBySoknadFra(filterObj, currObj) && isEqualByBrevkode(filterObj, currObj) && filterObj.erKlageEnhet == currObj.erKlageEnhet && filterObj.erFattetVedtak == currObj.erFattetVedtak && filterObj.erVedtakIkkeTilbakekreving == currObj.erVedtakIkkeTilbakekreving)

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
    vedtakType: v.vedtakType.filter((vt) => vt !== "KORRIGERING")
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
  })

  return existingVedtakType;
}, {})

fs.writeFileSync('../src/main/resources/files/dokument_valg.json', JSON.stringify(dokumentValgKtBySoknadGruppe, null, 2));