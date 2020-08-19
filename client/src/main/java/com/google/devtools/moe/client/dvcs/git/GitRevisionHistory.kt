/*
 * Copyright (c) 2011 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.devtools.moe.client.dvcs.git

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings
import com.google.common.base.Supplier
import com.google.devtools.moe.client.CommandRunner.CommandException
import com.google.devtools.moe.client.MoeProblem
import com.google.devtools.moe.client.repositories.AbstractRevisionHistory
import com.google.devtools.moe.client.repositories.Revision
import com.google.devtools.moe.client.repositories.RevisionGraph
import com.google.devtools.moe.client.repositories.RevisionHistory
import com.google.devtools.moe.client.repositories.RevisionHistory.Companion.MAX_REVISIONS_TO_SEARCH
import com.google.devtools.moe.client.repositories.RevisionHistory.SearchType
import com.google.devtools.moe.client.repositories.RevisionMatcher
import com.google.devtools.moe.client.repositories.RevisionMetadata
import java.util.ArrayDeque
import org.joda.time.format.DateTimeFormat

// Like ISO 8601 format, but without the 'T' character
private val GIT_DATE_FMT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss Z")

@VisibleForTesting
const val LOG_SEP = "---@MOE@---"
const val LOG_ENTRY_SEP = "---@MOE_LOG_ENTRY@---"

const val SINGLE_LINE_FORMAT = "%H$LOG_SEP%an <%ae>$LOG_SEP%ai$LOG_SEP%P$LOG_SEP%B$LOG_SEP"
const val MULTI_LINE_FORMAT = "$LOG_ENTRY_SEP$SINGLE_LINE_FORMAT"

/**
 * A Git implementation of [AbstractRevisionHistory].
 */
class GitRevisionHistory(headCloneSupplier: Supplier<GitClonedRepository>) : RevisionHistory {

  private val repo by lazy { headCloneSupplier.get() }

  override fun getMetadata(revision: Revision) =
    createMetadata(revision)?.let { finishMetadata(it) }

  fun getMetadata(logEntry: String) =
    parseMetadata(repo.repositoryName, logEntry)?.let { finishMetadata(it) }

  private fun finishMetadata(raw: RevisionMetadata): RevisionMetadata {
    // TODO(cgruber): Support git trailer parsing
    val parseResult = RevisionMetadata.legacyFieldParser(raw.description())
    val builder = raw.toBuilder()
    builder.description(parseResult.description())
    builder.fieldsBuilder().putAll(parseResult.fields())
    return builder.build()
  }

  /**
   * Confirm the existence of the given hash ID via 'git log', or pull the most recent
   * hash ID if none is given.
   *
   * @param revId a revision ID (or the name of a branch)
   * @return a Revision corresponding to the given revId hash
   */
  override fun findHighestRevision(revId: String?): Revision {
    val hashID = try {
      with(repo) {
        val args =
          listOf(
            "log",
            "--max-count=1",
            "--format=%H",
            if (revId.isNullOrBlank()) "HEAD" else revId,
            "--"
          ) + config.paths
        runGitCommand(*args.toTypedArray()).trim()
      }
    } catch (e: CommandException) {
      throw MoeProblem(e, "Failed git log run: %d %s %s", e.returnStatus, e.stdout, e.stderr)
    }
    return Revision(hashID, repo.repositoryName)
  }

  /**
   * Read the metadata for a given revision in the same repository.
   *
   * @param revision the revision to parse metadata for
   */
  private fun createMetadata(revision: Revision): RevisionMetadata? {
    if (repo.repositoryName != revision.repositoryName()) {
      throw MoeProblem(
        "Could not get metadata: Revision %s is in repository %s instead of %s",
        revision.revId(),
        revision.repositoryName(),
        repo.repositoryName
      )
    }

    // Format: hash, author, ISO date, parents, full commit message (subject and body)
    val log = try {
      val args = listOf(
        "log", // Ensure one revision only, to be safe.
        "--max-count=1",
        "--format=$SINGLE_LINE_FORMAT",
        "--ignore-missing",
        "--name-only",
        revision.revId,
        "--"
      )
      repo.runGitCommand(*args.toTypedArray())
    } catch (e: CommandException) {
      throw MoeProblem("Failed git run: %d %s %s", e.returnStatus, e.stdout, e.stderr)
    }
    return parseMetadata(repo.repositoryName, log)
  }

  /**
   * Parse the output of Git into RevisionMetadata.
   *
   * @param log the output of getMetadata to parse
   */
  @VisibleForTesting
  fun parseMetadata(repositoryName: String, log: String): RevisionMetadata? {
    if (Strings.isNullOrEmpty(log.trim())) { return null }

    // Split on the log delimiter. Limit to 5 so that it will act correctly
    // even if the log delimiter happens to be in the commit message.
    val elements = log.split(LOG_SEP, limit = 6)

    // The fourth item contains all of the parents, each separated by a space.
    val parents = elements[3].split(' ')
      .filterNot(String::isNullOrBlank)
      .map { Revision(it, repositoryName) }
    val files = elements[5].split("\n")
      .filterNot(String::isNullOrBlank)
    return RevisionMetadata.builder()
      .id(elements[0])
      .author(elements[1])
      .date(GIT_DATE_FMT.parseDateTime(elements[2]))
      .description(elements[4])
      .withParents(parents)
      .files(files)
      .build()
  }

  override fun <T> findRevisions(
    revision: Revision?,
    matcher: RevisionMatcher<T>,
    searchType: SearchType
  ): T {
    val start = revision ?: findHighestRevision("HEAD")
    val log = try {
      val args = listOf(
        "log", // Ensure one revision only, to be safe.
        "--max-count=$MAX_REVISIONS_TO_SEARCH",
        "--format=$MULTI_LINE_FORMAT",
        "--ignore-missing",
        "--name-only",
        start.revId(),
        "--"
      ) + repo.config.paths
      repo.runGitCommand(*args.toTypedArray())
    } catch (e: CommandException) {
      throw MoeProblem("Failed git run: %d %s %s", e.returnStatus, e.stdout, e.stderr)
    }

    val commits = log.splitToSequence(LOG_ENTRY_SEP).mapNotNull { getMetadata(it) }
    val newCommits = mutableListOf<RevisionMetadata>()
    val foo = commits.toList()
    for ((index, metadata) in foo.withIndex()) {
      val current = Revision(metadata.id(), repo.repositoryName)
      when {
        matcher.matches(current) -> {
          return matcher.makeResult(buildGraph(start, newCommits), listOf(current))
        }
        index == MAX_REVISIONS_TO_SEARCH -> throw MoeProblem(
          "Couldn't find a matching revision for $matcher from $start " +
            "within $MAX_REVISIONS_TO_SEARCH revisions."
        )

        else -> newCommits.add(metadata)
      }
    }
    // couldn't find an equivalence - return a result expressing such.
    return matcher.makeResult(buildGraph(start, newCommits), listOf())
  }

  private fun buildGraph(start: Revision, commits: MutableList<RevisionMetadata>): RevisionGraph {
    return RevisionGraph.builder(listOf(start))
      .apply {
        commits.let { if (repo.config.paths.isEmpty()) it else it.stitchLinear() }
          .forEach { addRevision(Revision(it.id(), start.repositoryName), it) }
      }.build()
  }
}

/**
 * Rework a list of commit metadata such that each item has the next as its parent.
 *
 * This is used when a partial graph is returned (due to a directory-constrained git-log) so that
 * the resulting commit space a valid history. This forces the graph into a linear parent-chain,
 * rendering graph-based search inappropriate.
 *
 * TODO(cgruber) Instead of this approach, parse the complete log, but ignore inappropriate commits.
 */
@VisibleForTesting
fun List<RevisionMetadata>.stitchLinear(): List<RevisionMetadata> {
  if (size < 2) return this
  val commits = ArrayDeque<RevisionMetadata>(this.reversed())
  var parent = commits.removeFirst().toBuilder().withParents().build()
  val newCommits = mutableListOf<RevisionMetadata>().apply { add(parent) }
  while (commits.isNotEmpty()) {
    val commit = commits.removeFirst()
    val repositoryName = commit.parents().first().repositoryName
    val modified = commit.toBuilder().withParents(Revision(parent.id(), repositoryName)).build()
    newCommits.add(modified)
    parent = modified
  }
  return newCommits.asReversed()
}
