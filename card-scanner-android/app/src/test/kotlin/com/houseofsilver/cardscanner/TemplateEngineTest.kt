package com.houseofsilver.cardscanner

import com.houseofsilver.cardscanner.domain.model.Contact
import com.houseofsilver.cardscanner.template.TemplateEngine
import org.junit.Assert.*
import org.junit.Test

class TemplateEngineTest {

    private fun contact(
        name: String = "Jane Doe",
        email: String = "jane@acme.com",
        role: String? = "Head of Operations",
        company: String? = "Acme AB"
    ) = Contact(name = name, email = email, role = role, company = company)

    @Test
    fun `all variables present - all substituted`() {
        val template = "Hi {first_name}, you work at {company} as {role}. Full name: {name}."
        val result = TemplateEngine.render(template, contact())

        assertFalse(result.contains("{first_name}"))
        assertFalse(result.contains("{name}"))
        assertFalse(result.contains("{role}"))
        assertFalse(result.contains("{company}"))

        assertTrue(result.contains("Jane"))
        assertTrue(result.contains("Acme AB"))
        assertTrue(result.contains("Head of Operations"))
        assertTrue(result.contains("Jane Doe"))
    }

    @Test
    fun `empty company shows fallback`() {
        val template = "You work at {company}."
        val result = TemplateEngine.render(template, contact(company = null))
        assertEquals("You work at your company.", result)
    }

    @Test
    fun `unknown variable is left as literal`() {
        val template = "Hello {frist_name}!"
        val result = TemplateEngine.render(template, contact())
        // Typo variable left untouched
        assertTrue(result.contains("{frist_name}"))
    }

    @Test
    fun `subject line with first_name extracts first token`() {
        val template = "Nice to meet you, {first_name}"
        val result = TemplateEngine.render(template, contact(name = "Jane Doe"))
        assertEquals("Nice to meet you, Jane", result)
    }

    @Test
    fun `first_name with single-word name`() {
        val template = "{first_name}"
        val result = TemplateEngine.render(template, contact(name = "Madonna"))
        assertEquals("Madonna", result)
    }

    @Test
    fun `empty name falls back to there`() {
        val template = "Hi {first_name}"
        val result = TemplateEngine.render(template, contact(name = ""))
        assertEquals("Hi there", result)
    }

    @Test
    fun `empty role shows fallback`() {
        val template = "Your role: {role}"
        val result = TemplateEngine.render(template, contact(role = null))
        assertEquals("Your role: your role", result)
    }

    @Test
    fun `findUnknownVariables returns typo variable`() {
        val template = "{first_name} and {frist_name} and {company_name}"
        val unknown = TemplateEngine.findUnknownVariables(template)
        assertTrue(unknown.contains("{frist_name}"))
        assertTrue(unknown.contains("{company_name}"))
        assertFalse(unknown.contains("{first_name}"))
    }

    @Test
    fun `findUnknownVariables empty when all known`() {
        val template = "{first_name} {name} {role} {company}"
        val unknown = TemplateEngine.findUnknownVariables(template)
        assertTrue(unknown.isEmpty())
    }
}
