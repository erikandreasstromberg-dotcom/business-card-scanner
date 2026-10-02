package com.houseofsilver.cardscanner.domain.model

data class Contact(
    val name: String,
    val email: String,
    val role: String? = null,
    val company: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val linkedin: String? = null,
    val address: String? = null
)
