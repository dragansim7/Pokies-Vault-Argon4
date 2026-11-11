package com.aistudio.pokiesvault.data.repository

import com.aistudio.pokiesvault.base.FirestoreEmulatorTestBase
import com.aistudio.pokiesvault.data.model.CatalogSite
import com.aistudio.pokiesvault.data.model.SiteGroup
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VaultRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveAndGetUserVault_authenticatedOwner_persistsAndReadsVaultState() = runBlocking {
        val uniqueEmail = "alice_${UUID.randomUUID()}@test.com"
        val uid = signInTestUser(uniqueEmail)
        val repository = VaultRepository(firestore)

        val sampleState = VaultState(
            visited = setOf("9au.com", "rock9.com"),
            mine = setOf("9au.com"),
            custom = listOf(CatalogSite("custom77.com", SiteGroup.CUSTOM, "Now")),
            themeId = "matrix"
        )

        val savedUid = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveVaultToFirestore(sampleState)
        }
        assertEquals(uid, savedUid)

        val loaded = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserVault(uid)
        }
        assertNotNull(loaded)
        assertTrue(loaded.mine.contains("9au.com"))
        assertTrue(loaded.visited.contains("rock9.com"))
        assertEquals("matrix", loaded.themeId)
    }

    @Test
    fun observeUserVault_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val uniqueEmail = "owner_${UUID.randomUUID()}@test.com"
        val uid = signInTestUser(uniqueEmail)
        val repository = VaultRepository(firestore)

        val sampleState = VaultState(
            visited = setOf("spinroo9.com"),
            mine = setOf("spinroo9.com"),
            themeId = "gold"
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveVaultToFirestore(sampleState)
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserVault(uid).first { it.mine.contains("spinroo9.com") }
        }
        assertEquals("gold", emitted.themeId)
    }

    @Test
    fun getUserVault_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceEmail = "alice_${UUID.randomUUID()}@test.com"
        val aliceUid = signInTestUser(aliceEmail)
        val aliceRepo = VaultRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveVaultToFirestore(VaultState(mine = setOf("9au.com")))
        }

        val bobEmail = "bob_${UUID.randomUUID()}@test.com"
        signInTestUser(bobEmail)
        val bobRepo = VaultRepository(firestore)

        val exception = assertFailsWith<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getUserVault(aliceUid)
            }
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, exception.code)
    }

    @Test
    fun observeUserVault_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceEmail = "alice_${UUID.randomUUID()}@test.com"
        val aliceUid = signInTestUser(aliceEmail)
        val repository = VaultRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveVaultToFirestore(VaultState(mine = setOf("9au.com")))
        }

        auth.signOut()

        val exception = assertFailsWith<Exception> {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeUserVault(aliceUid).first()
            }
        }
        val firestoreException = checkNotNull(exception.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
