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

import com.google.devtools.moe.client.CommandRunner
import com.google.devtools.moe.client.FileSystem
import com.google.devtools.moe.client.Lifetimes
import com.google.devtools.moe.client.Ui
import com.google.devtools.moe.client.config.RepositoryConfig
import java.io.File
import junit.framework.TestCase
import org.easymock.EasyMock

/**
 * Unit tests for GitClonedRepository.
 */
class GitClonedRepositoryTest : TestCase() {
  private val control = EasyMock.createControl()
  private val mockFS = control.createMock(FileSystem::class.java)
  private val cmd = control.createMock(CommandRunner::class.java)
  private val lifetimes = Lifetimes(Ui(System.err))
  private val repositoryName = "mockrepo"
  private val repositoryURL = "http://foo/git"
  private val localCloneTempDir = "/tmp/git_clone_mockrepo_12345"
  private var testBranch = "master"
  private var testIsShallow = false
  private var testSparse: List<String> = listOf()
  private fun branch() = if (testBranch == "master") null else testBranch
  private fun fakeConfig() = RepositoryConfig(
    type = "fake",
    url = repositoryURL,
    branch = if (testBranch == "master") null else testBranch,
    shallowCheckout = testIsShallow,
    paths = testSparse
  )

  @Throws(Exception::class) private fun expectCloneLocally(tags: Boolean = false) {
    // The Lifetimes of clones in these tests are arbitrary since we're not really creating any
    // temp dirs and we're not testing clean-up.
    val testBranchText = if (testBranch == "master") "" else "_$testBranch"
    EasyMock.expect(
      mockFS.getTemporaryDirectory(
        EasyMock.eq("git_clone_${repositoryName}${testBranchText}_"),
        EasyMock.anyObject()
      )
    )
      .andReturn(File(localCloneTempDir))
    EasyMock.expect(cmd.runCommand("", "git", listOf("init", localCloneTempDir)))
      .andReturn("git init ok (mock output)")
    EasyMock.expect(
      cmd.runCommand(
        localCloneTempDir,
        "git",
        listOf("remote", "add", "origin", repositoryURL)
      )
    )
      .andReturn("git add remote ok (mock output)")
    EasyMock.expect(
      cmd.runCommand(localCloneTempDir, "git", listOf("fetch", "origin", testBranch))
    ).andReturn("git fetch --tags (mock output)")
    if (testSparse.isNotEmpty()) {
      EasyMock.expect(
        cmd.runCommand(localCloneTempDir, "git", listOf("config", "core.sparseCheckout", "true"))
      ).andReturn("git config ok (mock output)")
      mockFS.write(
          testSparse.joinToString("\n", postfix = "\n"),
        File("$localCloneTempDir/.git/info/sparse-checkout")
      )
      EasyMock.expectLastCall<Any>()
    }
    if (testIsShallow) {
      EasyMock.expect(
        cmd.runCommand(
          localCloneTempDir,
          "git",
          listOf("pull", "--depth=1", "origin", testBranch)
        )
      )
        .andReturn("git pull ok (mock output)")
    } else {
      EasyMock.expect(
        cmd.runCommand(localCloneTempDir, "git", listOf("pull", "origin", testBranch))
      )
        .andReturn("git pull ok (mock output)")
    }
  }

  @Throws(Exception::class) private fun runTestCloneLocally() {
    expectCloneLocally()
    control.replay()
    val repo = GitClonedRepository(cmd, mockFS, repositoryName, fakeConfig(), lifetimes)
    repo.cloneLocallyAtHead(Lifetimes.persistent())
    assertEquals(repositoryName, repo.repositoryName)
    assertEquals(repositoryURL, repo.config.url)
    assertEquals(
      localCloneTempDir, repo.localTempDir
      .absolutePath
    )
    try {
      repo.cloneLocallyAtHead(Lifetimes.persistent())
      fail("Re-cloning repo succeeded unexpectedly.")
    } catch (expected: IllegalStateException) {
    }
    control.verify()
  }

  @Throws(Exception::class) fun testCloneLocally() {
    runTestCloneLocally()
  }

  @Throws(Exception::class) fun testCloneLocally_branch() {
    testBranch = "mybranch"
    runTestCloneLocally()
  }

  @Throws(Exception::class) fun testCloneLocally_shallow() {
    testIsShallow = true
    runTestCloneLocally()
  }

  @Throws(Exception::class) fun testCloneLocally_sparse() {
    testSparse = listOf("test/path/*", "test/path2/*")
    runTestCloneLocally()
  }

  @Throws(Exception::class) fun testUpdateToRevId_nonHeadRevId() {
    val updateRevId = "notHead"
    val headRevId = "head"
    expectCloneLocally()
    EasyMock.expect(
      cmd.runCommand(localCloneTempDir, "git", listOf("rev-parse", "HEAD"))
    )
      .andReturn(headRevId)

    // Updating to revision other than head, so create a branch.
    EasyMock.expect(
      cmd.runCommand(
        localCloneTempDir,
        "git",
        listOf(
          "checkout",
          updateRevId,
          "-b",
          GitClonedRepository.MOE_MIGRATIONS_BRANCH_PREFIX + updateRevId
        )
      )
    )
      .andReturn(headRevId)
    control.replay()
    val repo = GitClonedRepository(cmd, mockFS, repositoryName, fakeConfig(), lifetimes)
    repo.cloneLocallyAtHead(Lifetimes.persistent())
    repo.updateToRevision(updateRevId)
    control.verify()
  }

  @Throws(Exception::class) fun testUpdateToRevId_shallow() {
    testIsShallow = true
    val updateRevId = "notHead"
    val headRevId = "head"
    expectCloneLocally()
    EasyMock.expect(cmd.runCommand(localCloneTempDir, "git", listOf("rev-parse", "HEAD")))
      .andReturn(headRevId)

    // Unshallow the repository first.
    EasyMock.expect(
      cmd.runCommand(
        localCloneTempDir,
        "git",
        listOf("fetch", "--unshallow", "origin", testBranch))
    )
      .andReturn("git fetch unshallow ok (mock output)")

    // Updating to revision other than head, so create a branch.
    EasyMock.expect(
      cmd.runCommand(
        localCloneTempDir,
        "git",
        listOf(
          "checkout",
          updateRevId,
          "-b",
          GitClonedRepository.MOE_MIGRATIONS_BRANCH_PREFIX + updateRevId
        )
      )
    )
      .andReturn(headRevId)
    control.replay()
    val repo = GitClonedRepository(cmd, mockFS, repositoryName, fakeConfig(), lifetimes)
    repo.cloneLocallyAtHead(Lifetimes.persistent())
    repo.updateToRevision(updateRevId)
    control.verify()
  }

  @Throws(Exception::class) fun testUpdateToRevId_headRevId() {
    val updateRevId = "head"
    val headRevId = "head"
    expectCloneLocally()
    EasyMock.expect(cmd.runCommand(localCloneTempDir, "git", listOf("rev-parse", "HEAD")))
      .andReturn(headRevId)

    // No branch creation expected.
    control.replay()
    val repo = GitClonedRepository(cmd, mockFS, repositoryName, fakeConfig(), lifetimes)
    repo.cloneLocallyAtHead(Lifetimes.persistent())
    repo.updateToRevision(updateRevId)
    control.verify()
  }
}
