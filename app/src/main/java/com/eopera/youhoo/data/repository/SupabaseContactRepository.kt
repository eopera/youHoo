package com.eopera.youhoo.data.repository

import android.util.Log
import com.eopera.youhoo.core.model.Contact
import com.eopera.youhoo.data.remote.SupabaseUserDto
import com.eopera.youhoo.domain.repository.ContactRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SupabaseContactRepository @Inject constructor(
    private val supabase: SupabaseClient
) : ContactRepository {
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    override val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    override suspend fun refreshContacts(): Result<Unit> = try {
        _contacts.value = supabase.postgrest[USERS_TABLE]
            .select { order(DISPLAY_NAME_COLUMN, Order.ASCENDING) }
            .decodeList<SupabaseUserDto>()
            .map(SupabaseUserDto::toDomain)
        Result.success(Unit)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.e(TAG, "Contact refresh failed: ${error.javaClass.simpleName}")
        Result.failure(error)
    }

    private companion object {
        const val TAG = "SupabaseContactRepository"
        const val USERS_TABLE = "users"
        const val DISPLAY_NAME_COLUMN = "display_name"
    }
}
