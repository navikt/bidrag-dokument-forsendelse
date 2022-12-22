package no.nav.bidrag.dokument.forsendelse.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.swagger.v3.oas.annotations.media.Schema

@JsonIgnoreProperties(ignoreUnknown = true)
data class EnhetInfo(var enhetIdent: String, var enhetNavn: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SaksbehandlerInfoResponse(var ident: String, var navn: String)


@JsonIgnoreProperties(ignoreUnknown = true)
data class EnhetKontaktInfoDto(
    var enhetIdent: String? = null,
    var enhetNavn: String? = null,
    var telefonnummer: String? = null,
    var postadresse: EnhetPostadresseDto? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EnhetPostadresseDto(
    var postnummer: String? = null,
    var adresselinje1: String? = null,
    var adresselinje2: String? = null,
    var poststed: String? = null,
    var land: String? = null,
    var kommunenr: String? = null,
)