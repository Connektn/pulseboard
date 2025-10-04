package com.pulseboard.cdp.identity

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdentityGraphTest {
    private lateinit var graph: IdentityGraph

    @BeforeEach
    fun setup() {
        graph = IdentityGraph()
    }

    // === Normalization Tests ===

    @Test
    fun `normalize should lowercase emails`() {
        val normalized = graph.normalize("User@Example.COM")
        assertEquals("email:user@example.com", normalized)
    }

    @Test
    fun `normalize should trim whitespace`() {
        val normalized = graph.normalize("  user123  ")
        assertEquals("user:user123", normalized)
    }

    @Test
    fun `normalize should add user prefix for plain IDs`() {
        val normalized = graph.normalize("user123")
        assertEquals("user:user123", normalized)
    }

    @Test
    fun `normalize should add email prefix for email format`() {
        val normalized = graph.normalize("test@example.com")
        assertEquals("email:test@example.com", normalized)
    }

    @Test
    fun `normalize should add anon prefix for anonymous IDs`() {
        val normalized1 = graph.normalize("anon-abc123")
        assertEquals("anon:anon-abc123", normalized1)

        val normalized2 = graph.normalize("anonymous-xyz")
        assertEquals("anon:anonymous-xyz", normalized2)
    }

    @Test
    fun `normalize should preserve existing prefixes`() {
        assertEquals("user:123", graph.normalize("user:123"))
        assertEquals("email:test@example.com", graph.normalize("email:TEST@EXAMPLE.COM"))
        assertEquals("anon:abc", graph.normalize("anon:abc"))
    }

    @Test
    fun `normalize should handle whitespace with prefixes`() {
        assertEquals("user:123", graph.normalize("user: 123 "))
        assertEquals("email:test@example.com", graph.normalize("email: TEST@example.com "))
    }

    // === Union-Find Basic Tests ===

    @Test
    fun `find should return itself for new identifier`() {
        val id = "user:123"
        val found = graph.find(id)
        assertEquals(id, found)
    }

    @Test
    fun `find should normalize identifiers`() {
        val found1 = graph.find("User@Example.com")
        val found2 = graph.find("user@example.com")
        assertEquals(found1, found2)
        assertEquals("email:user@example.com", found1)
    }

    @Test
    fun `union should link two identifiers`() {
        graph.union("user:1", "user:2")

        val root1 = graph.find("user:1")
        val root2 = graph.find("user:2")

        assertEquals(root1, root2)
    }

    @Test
    fun `union should be idempotent`() {
        graph.union("user:1", "user:2")
        val root1 = graph.find("user:1")

        graph.union("user:1", "user:2")
        val root2 = graph.find("user:1")

        assertEquals(root1, root2)
    }

    // === Transitive Union Tests (DoD Requirement) ===

    @Test
    fun `unions should be transitive - if A to B and B to C then A to C`() {
        // Create chain: user:1 -> user:2 -> user:3
        graph.union("user:1", "user:2")
        graph.union("user:2", "user:3")

        val root1 = graph.find("user:1")
        val root2 = graph.find("user:2")
        val root3 = graph.find("user:3")

        // All should have the same root
        assertEquals(root1, root2)
        assertEquals(root2, root3)
        assertEquals(root1, root3)
    }

    @Test
    fun `unions should be transitive with complex chains`() {
        // Create: A-B, C-D, B-C -> all should be connected
        graph.union("user:A", "user:B")
        graph.union("user:C", "user:D")
        graph.union("user:B", "user:C")

        val rootA = graph.find("user:A")
        val rootB = graph.find("user:B")
        val rootC = graph.find("user:C")
        val rootD = graph.find("user:D")

        assertEquals(rootA, rootB)
        assertEquals(rootB, rootC)
        assertEquals(rootC, rootD)
    }

    @Test
    fun `unions should be transitive across different identifier types`() {
        // Link user -> email -> anon
        graph.union("user:123", "email:test@example.com")
        graph.union("email:test@example.com", "anon:abc-456")

        val rootUser = graph.find("user:123")
        val rootEmail = graph.find("email:test@example.com")
        val rootAnon = graph.find("anon:abc-456")

        assertEquals(rootUser, rootEmail)
        assertEquals(rootEmail, rootAnon)
    }

    @Test
    fun `unions should handle diamond pattern`() {
        // Create diamond: A-B, A-C, B-D, C-D
        graph.union("user:A", "user:B")
        graph.union("user:A", "user:C")
        graph.union("user:B", "user:D")
        graph.union("user:C", "user:D")

        val rootA = graph.find("user:A")
        val rootB = graph.find("user:B")
        val rootC = graph.find("user:C")
        val rootD = graph.find("user:D")

        assertEquals(rootA, rootB)
        assertEquals(rootA, rootC)
        assertEquals(rootA, rootD)
    }

    // === Canonical ID Tests (DoD Requirement) ===

    @Test
    fun `canonicalIdFor should return stable ID for single identifier`() {
        val canonical1 = graph.canonicalIdFor(listOf("user:123"))
        val canonical2 = graph.canonicalIdFor(listOf("user:123"))

        assertEquals(canonical1, canonical2)
        assertEquals("user:123", canonical1)
    }

    @Test
    fun `canonicalIdFor should return stable ID for multiple identifiers`() {
        val ids = listOf("user:123", "email:test@example.com", "anon:abc")

        val canonical1 = graph.canonicalIdFor(ids)
        val canonical2 = graph.canonicalIdFor(ids)

        assertEquals(canonical1, canonical2)
    }

    @Test
    fun `canonicalIdFor should return same ID regardless of order`() {
        val ids1 = listOf("user:123", "email:test@example.com", "anon:abc")
        val ids2 = listOf("anon:abc", "user:123", "email:test@example.com")
        val ids3 = listOf("email:test@example.com", "anon:abc", "user:123")

        val canonical1 = graph.canonicalIdFor(ids1)
        val canonical2 = graph.canonicalIdFor(ids2)
        val canonical3 = graph.canonicalIdFor(ids3)

        assertEquals(canonical1, canonical2)
        assertEquals(canonical2, canonical3)
    }

    @Test
    fun `canonicalIdFor should be deterministic with lexicographic ordering`() {
        // When unions have equal rank, lexicographically smaller should win
        val canonical = graph.canonicalIdFor(listOf("user:zzz", "user:aaa", "user:mmm"))

        // After unions, all should resolve to same root
        assertEquals(canonical, graph.find("user:zzz"))
        assertEquals(canonical, graph.find("user:aaa"))
        assertEquals(canonical, graph.find("user:mmm"))
    }

    @Test
    fun `canonicalIdFor should normalize identifiers`() {
        val canonical1 = graph.canonicalIdFor(listOf("Test@Example.COM", " user123 "))
        val canonical2 = graph.canonicalIdFor(listOf("test@example.com", "user123"))

        assertEquals(canonical1, canonical2)
    }

    // === Path Compression Tests ===

    @Test
    fun `path compression should flatten tree structure`() {
        // Create chain: 1->2->3->4
        graph.union("user:1", "user:2")
        graph.union("user:2", "user:3")
        graph.union("user:3", "user:4")

        // First find establishes roots
        val root = graph.find("user:1")

        // Second find should use path compression
        val root2 = graph.find("user:1")

        assertEquals(root, root2)

        // All nodes should now point directly to root
        assertEquals(root, graph.find("user:2"))
        assertEquals(root, graph.find("user:3"))
        assertEquals(root, graph.find("user:4"))
    }

    // === Email Normalization Tests (DoD Requirement) ===

    @Test
    fun `email normalization should handle various cases`() {
        val email1 = graph.normalize("TEST@EXAMPLE.COM")
        val email2 = graph.normalize("test@example.com")
        val email3 = graph.normalize("TeSt@ExAmPlE.cOm")

        assertEquals(email1, email2)
        assertEquals(email2, email3)
        assertEquals("email:test@example.com", email1)
    }

    @Test
    fun `email normalization should trim spaces`() {
        val email = graph.normalize("  test@example.com  ")
        assertEquals("email:test@example.com", email)
    }

    @Test
    fun `email normalization should work with existing prefix`() {
        val email = graph.normalize("email:TEST@EXAMPLE.COM")
        assertEquals("email:test@example.com", email)
    }

    @Test
    fun `union with normalized emails should link correctly`() {
        graph.union("TEST@EXAMPLE.COM", "user:123")
        graph.union("test@example.com", "anon:abc")

        val root1 = graph.find("TEST@EXAMPLE.COM")
        val root2 = graph.find("user:123")
        val root3 = graph.find("test@example.com")
        val root4 = graph.find("anon:abc")

        // All should resolve to same root
        assertEquals(root1, root2)
        assertEquals(root2, root3)
        assertEquals(root3, root4)
    }

    // === Integration Tests ===

    @Test
    fun `IDENTIFY event with multiple identifiers should union them`() {
        // Simulate IDENTIFY event with userId, email, and anonymousId
        val identifiers = listOf("user:user-123", "email:john@example.com", "anon:anon-abc")
        val canonical = graph.canonicalIdFor(identifiers)

        // All should resolve to same canonical ID
        assertEquals(canonical, graph.find("user:user-123"))
        assertEquals(canonical, graph.find("email:john@example.com"))
        assertEquals(canonical, graph.find("anon:anon-abc"))
    }

    @Test
    fun `ALIAS event should link two existing identifiers`() {
        // Simulate previous IDENTIFY events
        graph.canonicalIdFor(listOf("anon:anon-123"))
        graph.canonicalIdFor(listOf("user:user-456"))

        // Now ALIAS them together
        graph.union("anon:anon-123", "user:user-456")

        val root1 = graph.find("anon:anon-123")
        val root2 = graph.find("user:user-456")

        assertEquals(root1, root2)
    }

    @Test
    fun `complex identity resolution scenario`() {
        // Anonymous user visits
        val anon1 = graph.canonicalIdFor(listOf("anon:visitor-1"))

        // Signs up with email
        graph.union("anon:visitor-1", "email:user@example.com")

        // Later identified with userId
        graph.union("email:user@example.com", "user:12345")

        // Uses different device with different anon ID
        val anon2 = graph.canonicalIdFor(listOf("anon:visitor-2"))

        // Links via email again
        graph.union("anon:visitor-2", "email:user@example.com")

        // All identifiers should resolve to same canonical ID
        val canonical = graph.find("user:12345")
        assertEquals(canonical, graph.find("anon:visitor-1"))
        assertEquals(canonical, graph.find("anon:visitor-2"))
        assertEquals(canonical, graph.find("email:user@example.com"))
    }

    @Test
    fun `different identity graphs should be independent`() {
        // User A identifiers
        graph.canonicalIdFor(listOf("user:A", "email:a@example.com"))

        // User B identifiers
        graph.canonicalIdFor(listOf("user:B", "email:b@example.com"))

        val rootA = graph.find("user:A")
        val rootB = graph.find("user:B")

        // Should have different roots
        assertNotEquals(rootA, rootB)
    }
}
