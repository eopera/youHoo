package com.eopera.youhoo.core.model

/** Vendor-independent message used by repositories and the presentation layer. */
data class Message(
    val id: Long,
    val text: String,
    val sender: String,
    val createdAt: String
)
