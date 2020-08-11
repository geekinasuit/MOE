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
import com.google.common.collect.ImmutableList
import com.google.devtools.moe.client.CommandRunner.CommandException
import com.google.devtools.moe.client.MoeProblem
import com.google.devtools.moe.client.repositories.AbstractRevisionHistory
import com.google.devtools.moe.client.repositories.Revision
import com.google.devtools.moe.client.repositories.RevisionMetadata
import com.google.devtools.moe.client.repositories.RevisionMetadata.FieldParsingResult
import org.joda.time.format.DateTimeFormat

/**
 * A Git implementation of [AbstractRevisionHistory].
 */
class GitRevisionHistory(
  private val headCloneSupplier: Supplier<GitClonedRepository>
) : AbstractRevisionHistory() {

  /**
   * Confirm the existence of the given hash ID via 'git log', or pull the most recent
   * hash ID if none is given.
   *
   * @param revId a revision ID (or the name of a branch)
   * @return a Revision corresponding to the given revId hash
   */
  override fun findHighestRevision(revId: String?): Revision {
    val revId = if (revId.isNullOrBlank()) "HEAD" else revId
    val headClone = headCloneSupplier.get()
    val hashID = try {
      with(headClone) {
        val args = listOf("log", "--max-count=1", "--format=%H", revId!!, "--") + config.paths
        runGitCommand(*args.toTypedArray()).trim()
      }
    } catch (e: CommandException) {
      throw MoeProblem(e, "Failed git log run: %d %s %s", e.returnStatus, e.stdout, e.stderr)
    }
    return Revision(hashID, headClone.repositoryName)
  }

  /**
   * Read the metadata for a given revision in the same repository.
   *
   * @param revision the revision to parse metadata for
   */
  public override fun createMetadata(revision: Revision): RevisionMetadata {
    val headClone = headCloneSupplier.get()
    if (headClone.repositoryName != revision.repositoryName()) {
      throw MoeProblem(
        "Could not get metadata: Revision %s is in repository %s instead of %s",
        revision.revId(),
        revision.repositoryName(),
        headClone.repositoryName
      )
    }

    // Format: hash, author, ISO date, parents, full commit message (subject and body)
    val format = listOf("%H", "%an", "%ai", "%P", "%B").joinToString(LOG_DELIMITER)
    val log = try {
      val args = listOf(
        "log", // Ensure one revision only, to be safe.
        "--max-count=1",
        "--format=$format",
        "--ignore-missing",
        revision.revId(),
        "--"
      ) + headClone.config.paths
      headClone.runGitCommand(*args.toTypedArray())
    } catch (e: CommandException) {
      throw MoeProblem("Failed git run: %d %s %s", e.returnStatus, e.stdout, e.stderr)
    }
    return parseMetadata(headClone.repositoryName, log)!!
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
    val (id, author, timestamp, parentsText, description) = log.split(LOG_DELIMITER, limit = 5)

    // The fourth item contains all of the parents, each separated by a space.
    val parents = parentsText.split(' ')
      .filterNot(String::isNullOrBlank)
      .map { Revision(it, repositoryName) }
    return RevisionMetadata.builder()
      .id(id)
      .author(author)
      .date(GIT_DATE_FMT.parseDateTime(timestamp))
      .description(description)
      .withParents(parents)
      .build()
  }

  /** The tag parsing logic for git commits.  */
  override fun parseFields(metadata: RevisionMetadata): FieldParsingResult {
    // TODO(cgruber) implement trailer parsing
    return RevisionMetadata.legacyFieldParser(metadata.description())
  }

  override fun findHeadRevisions(): List<Revision> {
    // As distinct from Mercurial, the head (current branch) in Git can only ever point to a
    // single commit.
    return ImmutableList.of(
      findHighestRevision("HEAD")
    )
  }

  companion object {
    // Like ISO 8601 format, but without the 'T' character
    private val GIT_DATE_FMT =
      DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss Z")

    @VisibleForTesting
    val LOG_DELIMITER = "---@MOE@---"
  }
}
