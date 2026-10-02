package com.houseofsilver.cardscanner.template

import com.houseofsilver.cardscanner.domain.model.Contact

object TemplateEngine {

    val KNOWN_VARIABLES = setOf("{first_name}", "{name}", "{role}", "{company}")

    fun render(template: String, contact: Contact): String {
        val firstName = contact.name.split("\\s+".toRegex()).firstOrNull()?.takeIf { it.isNotBlank() } ?: "there"
        val name = contact.name.takeIf { it.isNotBlank() } ?: "there"
        val role = contact.role?.takeIf { it.isNotBlank() } ?: "your role"
        val company = contact.company?.takeIf { it.isNotBlank() } ?: "your company"

        return template
            .replace("{first_name}", firstName)
            .replace("{name}", name)
            .replace("{role}", role)
            .replace("{company}", company)
    }

    fun findUnknownVariables(template: String): List<String> {
        val variableRegex = Regex("""\{[^}]+\}""")
        return variableRegex.findAll(template)
            .map { it.value }
            .filter { it !in KNOWN_VARIABLES }
            .distinct()
            .toList()
    }
}
