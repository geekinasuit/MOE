package com.google.devtools.moe.client.database

import com.google.devtools.moe.client.repositories.Revision

/**
 * An Equivalence holds two Revisions which represent the same files as they appear
 * in different repositories
 *
 * <p>Two Revisions are equivalent when an Equivalence contains both in any order
 */
data class RepositoryEquivalence(
  val rev1: Revision,
  val rev2: Revision
) {
  init {
    require(rev1 != rev2) { "Identical revisions are already equivalent." }
  }

  val revisions: Map<String, Revision> = mapOf(
    rev1.repositoryName to rev1,
    rev2.repositoryName to rev2
  )

  override fun toString() = "$rev1 == $rev2"

  override fun equals(other: Any?) = other is RepositoryEquivalence &&
    other.revisions.values.toSet() == this.revisions.values.toSet()

  override fun hashCode() = 13 * (13 + revisions.hashCode())

  /** @return the Revision in this Equivalence for the given repository name */
  operator fun get(repositoryName: String): Revision? {
    require(revisions.containsKey(repositoryName)) {
      "Equivalence {$this} doesn't have revision for $repositoryName"
    }
    return revisions[repositoryName]
  }

  /**
   * @param revision a Revision in this Equivalence
   *
   * @return the other Revision in this Equivalence, or null if this Equivalence
   * does not contain [revision] as one of its [revisions]
   */
  operator fun get(revision: Revision): Revision? {
    return when (revision) {
      rev1 -> rev2
      rev2 -> rev1
      else -> null
    }
  }

  /**
   * @param revision the Revision to look for in this Equivalence
   *
   * @return true if this Equivalence has revision as one of its Revisions
   */
  fun hasRevision(revision: Revision) = rev1 == revision || rev2 == revision
}
