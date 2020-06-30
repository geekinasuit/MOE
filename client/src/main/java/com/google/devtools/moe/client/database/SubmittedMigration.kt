package com.google.devtools.moe.client.database

import com.google.devtools.moe.client.repositories.Revision

/**
 * A {@code SubmittedMigration} holds information about a completed migration.
 *
 * <p>It differs from an {@code Equivalence} in that a {@code SubmittedMigration} has a
 * direction associated with its Revisions.
 */
data class SubmittedMigration(
  val from: Revision,
  val to: Revision
) {

  override fun toString() = "$from ==> $to"
}
