package com.example.vaultix.data

data class PasswordEntry(
    val id: String,
    val site: String,
    val encryptedPassword: String,
    val iv: String
)

data class UiPasswordEntry(
    val id: String,
    val site: String,
    val password: String,
    val isVisible: Boolean = false
)
