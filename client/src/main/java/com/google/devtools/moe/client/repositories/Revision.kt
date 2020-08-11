package com.google.devtools.moe.client.repositories

import com.google.common.collect.ImmutableList
import com.google.devtools.moe.client.MoeProblem
import com.google.devtools.moe.client.codebase.expressions.RepositoryExpression
import com.google.devtools.moe.client.project.ProjectContext
import com.squareup.moshi.Json

data class Revision(
  @Json(name = "rev_id") val revId: String,
  @Json(name = "repository_name") val repositoryName: String
) {
  constructor(
    revId: Long,
    repositoryName: String
  ) : this(revId = revId.toString(), repositoryName = repositoryName)

  override fun toString(): String = "$repositoryName{$revId}"

  fun revId() = revId // legacy autovalue accessor
  fun repositoryName() = repositoryName // legacy autovalue accessor

  companion object {
    /**
     * Return the list of Revisions given by a RepositoryExpression like "internal(revision=3,4,5)".
     */
    @JvmStatic fun fromRepositoryExpression(
      repoEx: RepositoryExpression,
      context: ProjectContext
    ): List<Revision> {
      val repo = context.getRepository(repoEx.repositoryName)
      val revision = repoEx.getOption("revision")
      if (revision.isNullOrBlank()) {
        throw MoeProblem(
          "Repository expression '$repoEx' must have a 'revision' option, " +
            "e.g. internal(revision=3,4,5)."
        )
      }
      val rh = repo.revisionHistory()
      return ImmutableList.builder<Revision>()
          .addAll(revision.split(",").map { rh.findHighestRevision(it) })
          .build()
    }
  }
}
