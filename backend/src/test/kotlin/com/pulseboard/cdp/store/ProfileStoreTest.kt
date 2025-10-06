package com.pulseboard.cdp.store

import com.pulseboard.cdp.model.ProfileIdentifiers
import com.pulseboard.fixedClock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileStoreTest {
    private lateinit var store: ProfileStore

    @BeforeEach
    fun setup() {
        store = ProfileStore()
    }

    @Test
    fun `should return null for non-existent profile`() {
        val profile = store.get("non-existent")
        assertNull(profile)
    }

    @Test
    fun `should create profile with default values`() {
        val profileId = "profile-1"
        val profile = store.getOrCreate(profileId)

        assertNotNull(profile)
        assertEquals(profileId, profile.profileId)
        assertEquals(0, profile.identifiers.userIds.size)
        assertEquals(0, profile.identifiers.emails.size)
        assertEquals(0, profile.identifiers.anonymousIds.size)
        assertEquals(0, profile.traits.size)
        assertEquals(0, profile.counters.size)
        assertEquals(0, profile.segments.size)
        assertEquals(Instant.EPOCH, profile.lastSeen)
    }

    @Test
    fun `should merge identifiers correctly`() {
        val profileId = "profile-1"

        // First merge
        val identifiers1 =
            ProfileIdentifiers(
                userIds = setOf("user:123"),
                emails = setOf("email:test@example.com"),
                anonymousIds = setOf("anon:abc"),
            )
        store.mergeIdentifiers(profileId, identifiers1)

        var profile = store.get(profileId)
        assertNotNull(profile)
        assertEquals(1, profile.identifiers.userIds.size)
        assertEquals(1, profile.identifiers.emails.size)
        assertEquals(1, profile.identifiers.anonymousIds.size)

        // Second merge (union)
        val identifiers2 =
            ProfileIdentifiers(
                userIds = setOf("user:456"),
                emails = setOf("email:test2@example.com"),
                anonymousIds = emptySet(),
            )
        store.mergeIdentifiers(profileId, identifiers2)

        profile = store.get(profileId)
        assertNotNull(profile)
        assertEquals(2, profile.identifiers.userIds.size)
        assertTrue(profile.identifiers.userIds.contains("user:123"))
        assertTrue(profile.identifiers.userIds.contains("user:456"))
        assertEquals(2, profile.identifiers.emails.size)
        assertEquals(1, profile.identifiers.anonymousIds.size)
    }

    @Test
    fun `should apply LWW for traits with newer timestamp`() {
        val profileId = "profile-1"
        val t1 = Instant.parse("2025-01-01T10:00:00Z")
        val t2 = Instant.parse("2025-01-01T10:01:00Z")

        // Set trait at T1
        store.mergeTraits(profileId, mapOf("plan" to "basic"), t1)

        var profile = store.get(profileId)
        assertEquals("basic", profile?.traits?.get("plan"))

        // Update trait at T2 (newer)
        store.mergeTraits(profileId, mapOf("plan" to "pro"), t2)

        profile = store.get(profileId)
        assertEquals("pro", profile?.traits?.get("plan"))
    }

    @Test
    fun `should reject older trait update (LWW)`() {
        val profileId = "profile-1"
        val t1 = Instant.parse("2025-01-01T10:00:00Z")
        val t2 = Instant.parse("2025-01-01T09:59:00Z") // Older

        // Set trait at T1
        store.mergeTraits(profileId, mapOf("plan" to "pro"), t1)

        var profile = store.get(profileId)
        assertEquals("pro", profile?.traits?.get("plan"))

        // Try to update with older timestamp T2
        store.mergeTraits(profileId, mapOf("plan" to "basic"), t2)

        // Should still be "pro"
        profile = store.get(profileId)
        assertEquals("pro", profile?.traits?.get("plan"))
    }

    @Test
    fun `should allow same timestamp trait update (LWW)`() {
        val profileId = "profile-1"
        val t1 = Instant.parse("2025-01-01T10:00:00Z")

        // Set trait at T1
        store.mergeTraits(profileId, mapOf("plan" to "basic"), t1)

        // Update with same timestamp T1
        store.mergeTraits(profileId, mapOf("plan" to "pro"), t1)

        // Should accept the update (>= comparison)
        val profile = store.get(profileId)
        assertEquals("pro", profile?.traits?.get("plan"))
    }

    @Test
    fun `should merge multiple traits independently with LWW`() {
        val profileId = "profile-1"
        val t1 = Instant.parse("2025-01-01T10:00:00Z")
        val t2 = Instant.parse("2025-01-01T10:01:00Z")
        val t3 = Instant.parse("2025-01-01T09:59:00Z")

        // Set traits at T1
        store.mergeTraits(profileId, mapOf("plan" to "basic", "tier" to "bronze"), t1)

        var profile = store.get(profileId)
        assertEquals("basic", profile?.traits?.get("plan"))
        assertEquals("bronze", profile?.traits?.get("tier"))

        // Update plan at T2 (newer), tier at T3 (older)
        store.mergeTraits(profileId, mapOf("plan" to "pro"), t2)
        store.mergeTraits(profileId, mapOf("tier" to "silver"), t3)

        profile = store.get(profileId)
        assertEquals("pro", profile?.traits?.get("plan")) // Updated (newer)
        assertEquals("bronze", profile?.traits?.get("tier")) // Not updated (older)
    }

    @Test
    fun `should update lastSeen only if newer`() {
        val profileId = "profile-1"
        val t1 = Instant.parse("2025-01-01T10:00:00Z")
        val t2 = Instant.parse("2025-01-01T10:01:00Z")
        val t3 = Instant.parse("2025-01-01T09:59:00Z")

        // Create profile with T1
        store.getOrCreate(profileId)
        store.updateLastSeen(profileId, t1)

        var profile = store.get(profileId)
        assertEquals(t1, profile?.lastSeen)

        // Update with newer T2
        store.updateLastSeen(profileId, t2)
        profile = store.get(profileId)
        assertEquals(t2, profile?.lastSeen)

        // Try to update with older T3
        store.updateLastSeen(profileId, t3)
        profile = store.get(profileId)
        assertEquals(t2, profile?.lastSeen) // Should still be T2
    }

    @Test
    fun `should update counters`() {
        val profileId = "profile-1"

        store.updateCounters(profileId, mapOf("events" to 5L, "page_views" to 10L))

        val profile = store.get(profileId)
        assertNotNull(profile)
        assertEquals(5L, profile.counters["events"])
        assertEquals(10L, profile.counters["page_views"])
    }

    @Test
    fun `should update segments`() {
        val profileId = "profile-1"

        store.updateSegments(profileId, setOf("power_user", "pro_plan"))

        val profile = store.get(profileId)
        assertNotNull(profile)
        assertEquals(2, profile.segments.size)
        assertTrue(profile.segments.contains("power_user"))
        assertTrue(profile.segments.contains("pro_plan"))
    }

    @Test
    fun `should track profile count`() {
        assertEquals(0, store.size())

        store.getOrCreate("profile-1")
        assertEquals(1, store.size())

        store.getOrCreate("profile-2")
        assertEquals(2, store.size())

        // Getting same profile shouldn't increase count
        store.getOrCreate("profile-1")
        assertEquals(2, store.size())
    }

    @Test
    fun `should clear all state`() {
        store.getOrCreate("profile-1")
        store.mergeTraits("profile-1", mapOf("plan" to "pro"), fixedClock.instant())

        assertEquals(1, store.size())

        store.clear()

        assertEquals(0, store.size())
        assertNull(store.get("profile-1"))
    }
}
