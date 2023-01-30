package no.nav.bidrag.dokument.forsendelse.model

import kotlin.reflect.full.memberProperties

inline fun <T> Boolean?.ifTrue(block: Boolean.() -> T): T? {
    return if (this == true)  block() else null
}

fun Any.toStringByReflection(exclude: List<String> = listOf(), mask: List<String> = listOf()): String {
    val propsString = this::class.memberProperties
        .filter { exclude.isEmpty() || !exclude.contains(it.name) }
        .joinToString(", ") {
            val value = if (mask.isNotEmpty() && mask.contains(it.name)) "****" else it.getter.call(this).toString()
            "${it.name}=${value}"
        };

    return "${this::class.simpleName}[${propsString}]"
}