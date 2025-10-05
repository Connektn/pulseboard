package com.pulseboard.cdp.identity

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Identity graph using union-find algorithm with path compression.
 * Resolves multiple identifiers (user:, email:, anon:) to a canonical profileId.
 */
@Component
class IdentityGraph {
    // Parent mapping for union-find
    private val parent = ConcurrentHashMap<String, String>()

    // Rank for union by rank optimization
    private val rank = ConcurrentHashMap<String, Int>()

    /**
     * Find the canonical (root) identifier for the given id with path compression.
     *
     * @param id The identifier to find
     * @return The canonical identifier
     */
    fun find(id: String): String {
        val normalizedId = normalize(id)

        // If not in parent map, it's its own parent
        if (!parent.containsKey(normalizedId)) {
            parent[normalizedId] = normalizedId
            rank[normalizedId] = 0
            return normalizedId
        }

        // Path compression: make every node point directly to root
        val currentParent = parent[normalizedId]!!
        if (currentParent != normalizedId) {
            parent[normalizedId] = find(currentParent)
        }

        return parent[normalizedId]!!
    }

    /**
     * Union two identifier sets.
     * Uses union by rank for optimization.
     *
     * @param a First identifier
     * @param b Second identifier
     */
    fun union(a: String, b: String) {
        val rootA = find(a)
        val rootB = find(b)

        // Already in same set
        if (rootA == rootB) {
            return
        }

        // Union by rank: attach smaller tree under larger tree
        val rankA = rank[rootA] ?: 0
        val rankB = rank[rootB] ?: 0

        when {
            rankA < rankB -> parent[rootA] = rootB
            rankA > rankB -> parent[rootB] = rootA
            else -> {
                // If ranks are equal, choose lexicographically smaller as root for determinism
                if (rootA < rootB) {
                    parent[rootB] = rootA
                    rank[rootA] = rankA + 1
                } else {
                    parent[rootA] = rootB
                    rank[rootB] = rankB + 1
                }
            }
        }
    }

    /**
     * Get canonical ID for a list of identifiers.
     * Returns a stable, deterministic canonical ID.
     *
     * @param ids List of identifiers
     * @return Canonical identifier (lexicographically smallest root)
     */
    fun canonicalIdFor(ids: List<String>): String {
        require(ids.isNotEmpty()) { "Cannot get canonical ID for empty list" }

        // Normalize all IDs and union them together
        val normalizedIds = ids.map { normalize(it) }

        // Union all IDs together
        for (i in 1 until normalizedIds.size) {
            union(normalizedIds[0], normalizedIds[i])
        }

        // Find the canonical ID (root)
        return find(normalizedIds[0])
    }

    /**
     * Normalize an identifier by:
     * - Adding appropriate prefix (user:, email:, anon:) if not present
     * - Lowercasing emails
     * - Trimming whitespace
     *
     * @param id The identifier to normalize
     * @return Normalized identifier
     */
    fun normalize(id: String): String {
        val trimmed = id.trim()

        // Already has a prefix
        if (trimmed.startsWith("user:") ||
            trimmed.startsWith("email:") ||
            trimmed.startsWith("anon:")) {

            // Extract prefix and value
            val parts = trimmed.split(":", limit = 2)
            if (parts.size != 2) {
                return trimmed
            }

            val prefix = parts[0]
            val value = parts[1].trim()

            // Lowercase emails
            return if (prefix == "email") {
                "$prefix:${value.lowercase()}"
            } else {
                "$prefix:$value"
            }
        }

        // Infer prefix based on format
        return when {
            // Email format (contains @)
            trimmed.contains("@") -> "email:${trimmed.lowercase()}"
            // Anonymous ID format (contains "anon" or starts with "anon-")
            trimmed.contains("anon", ignoreCase = true) ||
            trimmed.startsWith("anon-", ignoreCase = true) -> "anon:$trimmed"
            // Default to user ID
            else -> "user:$trimmed"
        }
    }

    /**
     * Get all known identifiers (for debugging/testing).
     */
    fun getAllIdentifiers(): Set<String> = parent.keys.toSet()

    /**
     * Clear all data (for testing).
     */
    fun clear() {
        parent.clear()
        rank.clear()
    }
}
