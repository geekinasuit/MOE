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

import com.google.common.collect.ImmutableList
import com.google.devtools.moe.client.CommandRunner
import com.google.devtools.moe.client.CommandRunner.CommandException
import com.google.devtools.moe.client.FileSystem
import com.google.devtools.moe.client.FileSystem.Lifetime
import com.google.devtools.moe.client.Lifetimes
import com.google.devtools.moe.client.MoeProblem
import com.google.devtools.moe.client.codebase.LocalWorkspace
import com.google.devtools.moe.client.config.RepositoryConfig
import java.io.File
import java.io.IOException
import java.nio.file.Paths

/**
 * Git implementation of [LocalWorkspace], i.e. a 'git clone' to local disk.
 */
// open for mocking
open class GitClonedRepository(
  private val cmd: CommandRunner,
  private val filesystem: FileSystem,
  private val repositoryName: String,
  private val config: RepositoryConfig,
  /**
   * The location to clone from. If snapshotting a locally modified Writer, this will _not_ be
   * the same as repositoryConfig.getUrl(). Otherwise, it will.
   */
  private val repositoryUrl: String?,
  private val lifetimes: Lifetimes
) : LocalWorkspace {

  private lateinit var localCloneTempDir: File
  private var clonedLocally = false
  /** The revision of this clone, a Git hash ID  */
  private var revId: String? = null

  constructor(
    cmd: CommandRunner,
    filesystem: FileSystem,
    repositoryName: String,
    config: RepositoryConfig,
    lifetimes: Lifetimes
  ) : this(cmd, filesystem, repositoryName, config, config.url, lifetimes)

  override fun getRepositoryName() = repositoryName

  override fun getConfig() = config

  override fun getLocalTempDir() = localCloneTempDir.also { check(clonedLocally) }

  @Throws(CommandException::class, IOException::class)
  private fun initLocal(dir: File?) {
    cmd.runCommand("", "git", ImmutableList.of("init", dir!!.absolutePath))
    cmd.runCommand(dir.absolutePath, "git", listOf("remote", "add", "origin", repositoryUrl))
    val fetchArgs = listOf("fetch") +
      (config.tag?.let { listOf("--tags") } ?: listOf()) +
      (config.depth?.let { listOf("--depth=${config.depth}") } ?: listOf()) +
      "origin" +
      (config.branch ?: config.tag ?: DEFAULT_BRANCH)

    cmd.runCommand(dir.absolutePath, "git", fetchArgs)
    if (config.paths.isNotEmpty()) {
      cmd.runCommand(dir.absolutePath, "git", listOf("config", "core.sparseCheckout", "true"))
      filesystem.write(
        """${java.lang.String.join("\n", config.paths)}

          """.trimIndent(),
        Paths.get(dir.absolutePath, ".git", "info", "sparse-checkout").toFile()
      )
    }
  }

  override fun cloneLocallyAtHead(cloneLifetime: Lifetime) {
    check(!clonedLocally)
    val tempDirName = when {
      config.branch != null -> "git_clone_${repositoryName}_${config.branch}_"
      config.tag != null -> "git_clone_${repositoryName}_${config.tag}_"
      else -> "git_clone_${repositoryName}_"
    }
    localCloneTempDir = filesystem.getTemporaryDirectory(tempDirName, cloneLifetime)
    try {
      initLocal(localCloneTempDir)
      val pullArgs = listOf("pull") +
        (config.tag?.let { listOf("--tags") } ?: listOf()) +
        when {
          config.shallowCheckout -> listOf("--depth=1")
          config.depth != null -> listOf("--depth=${config.depth}")
          else -> listOf()
        } +
        "origin" +
        (config.branch ?: config.tag ?: DEFAULT_BRANCH)
      cmd.runCommand(localCloneTempDir.absolutePath, "git", pullArgs)
      clonedLocally = true
      revId = HEAD
    } catch (e: CommandException) {
      throw MoeProblem(e, "Could not clone from git repo at $repositoryUrl: ${e.stderr}")
    } catch (e: IOException) {
      throw MoeProblem(e, "Could not clone from git repo at $repositoryUrl: ${e.message}")
    }
  }

  override fun updateToRevision(revId: String) {
    check(clonedLocally)
    check(this.revId == HEAD)
    try {
      val headHash = runGitCommand("rev-parse", HEAD).trim { it <= ' ' }
      // If we are updating to a revision other than the branch's head, branch from that revision.
      // Otherwise, no update/checkout is necessary since we are already at the desired revId,
      // branch head.
      if (headHash != revId) {
        if (config.shallowCheckout) {
          // Unshallow the repository to enable checking out given revId.
          runGitCommand(
            "fetch",
            "--unshallow",
            "origin",
            config.branch ?: config.tag ?: DEFAULT_BRANCH)
        }
        runGitCommand("checkout", revId, "-b", MOE_MIGRATIONS_BRANCH_PREFIX + revId)
      }
      this.revId = revId
    } catch (e: CommandException) {
      throw MoeProblem(e, "Could not update git repo at %s: %s", localTempDir, e.stderr)
    }
  }

  override fun archiveAtRevision(revId: String?): File {
    check(clonedLocally)
    val revId = if (revId.isNullOrEmpty()) HEAD else revId
    val archiveLocation = filesystem.getTemporaryDirectory(
      "git_archive_${repositoryName}_${revId}_", lifetimes.currentTask()
    )
    try {
      filesystem.makeDirs(archiveLocation)
      if (config.paths.isEmpty()) {
        // Using this just to get a filename.
        val tarballPath = filesystem.getTemporaryDirectory(
          "git_tarball_${repositoryName}_$revId.tar.", lifetimes.currentTask()
        ).absolutePath

        // Git doesn't support archiving to a directory: it only supports
        // archiving to a tar.  The fastest way to do this would be to pipe the
        // output directly into tar, however, there's no option for that using
        // the classes we have. (michaelpb)
        runGitCommand("archive", "--format=tar", "--output=$tarballPath", revId!!)

        // Untar the tarball we just made
        cmd.runCommand(
          "", "tar", listOf("xf", tarballPath, "-C", archiveLocation.absolutePath)
        )
      } else {
        initLocal(archiveLocation)
        val pullArgs = listOf("pull") +
          (config.tag?.let { listOf("--tags") } ?: listOf()) +
          when {
            config.shallowCheckout -> listOf("--depth=1")
            config.depth != null -> listOf("--depth=${config.depth}")
            else -> listOf()
          } +
          "origin" +
          (config.branch ?: config.tag ?: DEFAULT_BRANCH)
        cmd.runCommand(archiveLocation.absolutePath, "git", pullArgs)
        cmd.runCommand(archiveLocation.absolutePath, "git", listOf("checkout", revId))
        // Remove git tracking.
        filesystem.deleteRecursively(
          Paths.get(archiveLocation.absolutePath, ".git").toFile()
        )
      }
    } catch (e: CommandException) {
      throw MoeProblem(e, "Could not archive git clone at ${localTempDir.absolutePath}")
    } catch (e: IOException) {
      throw MoeProblem(
        e,
        "IOException archiving clone at ${localTempDir.absolutePath} to revision $revId"
      )
    }
    return archiveLocation
  }

  /**
   * Runs a git command with the given arguments, in this cloned repository's directory.
   *
   * @param args a list of arguments for git
   * @return a string containing the STDOUT result
   */
  @Throws(CommandException::class)
  // open for testing
  open fun runGitCommand(vararg args: String): String {
    return cmd
      .runCommand(
        localTempDir.absolutePath,
        "git",
        args.toList()
      )
  }

  companion object {
    /**
     * A prefix for branches MOE creates to write migrated changes. For example, if there have been
     * revisions in a to-repository since an equivalence revision, MOE won't try to merge or rebase
     * those changes -- instead, it will create a branch with this prefix from the equivalence
     * revision.
     */
    const val MOE_MIGRATIONS_BRANCH_PREFIX = "moe_writing_branch_from_"
    const val DEFAULT_BRANCH = "master"
    const val HEAD = "HEAD"
  }
}
