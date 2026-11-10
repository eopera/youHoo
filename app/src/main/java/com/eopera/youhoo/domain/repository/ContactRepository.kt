package com.eopera.youhoo.domain.repository

import com.eopera.youhoo.core.model.Contact
import kotlinx.coroutines.flow.StateFlow

interface ContactRepository {
    val contacts: StateFlow<List<Contact>>
    suspend fun refreshContacts(): Result<Unit>
}
