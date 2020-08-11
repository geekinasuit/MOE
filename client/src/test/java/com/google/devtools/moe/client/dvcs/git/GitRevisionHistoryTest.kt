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

import com.google.common.base.Joiner
import com.google.common.base.Suppliers
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import com.google.devtools.moe.client.CommandRunner.CommandException
import com.google.devtools.moe.client.MoeProblem
import com.google.devtools.moe.client.config.RepositoryConfig
import com.google.devtools.moe.client.database.DbStorage
import com.google.devtools.moe.client.database.FileDb
import com.google.devtools.moe.client.database.RepositoryEquivalence
import com.google.devtools.moe.client.database.RepositoryEquivalenceMatcher
import com.google.devtools.moe.client.moshi.MoshiModule.Companion.provideMoshi
import com.google.devtools.moe.client.repositories.Revision
import com.google.devtools.moe.client.repositories.RevisionHistory.SearchType.BRANCHED
import com.google.devtools.moe.client.repositories.RevisionHistory.SearchType.LINEAR
import com.google.devtools.moe.client.testing.DummyDb
import junit.framework.TestCase
import org.easymock.EasyMock
import org.easymock.IExpectationSetters
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

/**
 * Unit tests for GitRevisionHistory.
 */
class GitRevisionHistoryTest : TestCase() {
  companion object {
    private const val GIT_COMMIT_DATE = "2012-07-09 06:00:00 -0700"
    private val DATE = // 2012/7/9, 6am
      DateTime(2012, 7, 9, 6, 0, DateTimeZone.forOffsetHours(-7))
    private const val LOG_FORMAT_COMMIT_ID = "%H"
    private val METADATA_JOINER = Joiner.on(GitRevisionHistory.LOG_DELIMITER)
    private val LOG_FORMAT_ALL_METADATA = METADATA_JOINER.join("%H", "%an", "%ai", "%P", "%B")
  }

  private val control = EasyMock.createControl()
  private val repositoryName = "mockrepo"
  private val localCloneTempDir = "/tmp/git_tipclone_mockrepo_12345"

  @Throws(CommandException::class)
  private fun mockClonedRepo(repoName: String): GitClonedRepository {
    val mockRepo = control.createMock(GitClonedRepository::class.java)
    val repositoryConfig = RepositoryConfig(type = "fake", url = localCloneTempDir)
    EasyMock.expect(mockRepo.repositoryName).andReturn(repoName).anyTimes()
    EasyMock.expect(mockRepo.config).andReturn(repositoryConfig).anyTimes()
    return mockRepo
  }

  @Throws(CommandException::class)
  private fun expectLogCommandIgnoringMissing(
    mockRepo: GitClonedRepository,
    logFormat: String,
    revName: String
  ): IExpectationSetters<String> {
    return EasyMock.expect(
      mockRepo.runGitCommand(
        "log", "--max-count=1", "--format=$logFormat", "--ignore-missing", revName, "--"
      )
    )
  }

  @Throws(CommandException::class)
  private fun expectLogCommand(
    mockRepo: GitClonedRepository,
    logFormat: String,
    revName: String
  ): IExpectationSetters<String> {
    return EasyMock.expect(
      mockRepo.runGitCommand("log", "--max-count=1", "--format=$logFormat", revName, "--")
    )
  }

  @Throws(Exception::class) fun testFindHighestRevision() {
    val mockRepo = mockClonedRepo(repositoryName)
    expectLogCommand(mockRepo, LOG_FORMAT_COMMIT_ID, "HEAD").andReturn("mockHashID")
    control.replay()
    val rh = GitRevisionHistory(Suppliers.ofInstance(mockRepo))
    val (revId, repositoryName1) = rh.findHighestRevision(null)
    assertEquals(repositoryName, repositoryName1)
    assertEquals("mockHashID", revId)
    control.verify()
  }

  @Throws(Exception::class)
  fun testFindHighestRevision_nonExistentHashThrows() {
    val mockRepo = mockClonedRepo(repositoryName)
    expectLogCommand(mockRepo, LOG_FORMAT_COMMIT_ID, "bogusHash")
      .andThrow(
        CommandException(
          "git",
          ImmutableList.of("mock args"),
          "mock stdout",
          "mock stderr: unknown revision",
          1
        )
      )
    control.replay()
    try {
      val rh =
        GitRevisionHistory(
          Suppliers.ofInstance(
            mockRepo
          )
        )
      rh.findHighestRevision("bogusHash")
      fail("'git log' didn't fail on bogus hash ID")
    } catch (expected: MoeProblem) {
    }
    control.verify()
  }

  @Throws(Exception::class) fun testGetMetadata() {
    val mockRepo = mockClonedRepo(repositoryName)
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "1")
      .andReturn(
        METADATA_JOINER.join("1", "foo@google.com", GIT_COMMIT_DATE, "2 3", "description\n")
      )
    control.replay()
    val rh = GitRevisionHistory(Suppliers.ofInstance(mockRepo))
    val result = rh.getMetadata(Revision(1, "mockrepo"))
    assertEquals("1", result.id())
    assertEquals("foo@google.com", result.author())
    assertThat(result.date()).isEquivalentAccordingToCompareTo(DATE)
    assertEquals("description\n", result.description())
    assertThat(result.parents())
      .containsExactly(Revision(2, repositoryName), Revision(3, repositoryName))
      .inOrder()
    control.verify()
  }

  @Throws(CommandException::class)
  fun testParseMetadata_multiLine() {
    val rh = GitRevisionHistory(Suppliers.ofInstance(mockClonedRepo(repositoryName)))
    control.replay()
    val rm = rh.parseMetadata(
      repositoryName,
      METADATA_JOINER.join(
        "1",
        "foo@google.com",
        GIT_COMMIT_DATE,
        "2 3",
        "desc with \n\nmultiple lines\n"
      )
    )!!
    control.verify()
    assertEquals("1", rm.id())
    assertEquals("foo@google.com", rm.author())
    assertThat(rm.date())
      .isEquivalentAccordingToCompareTo(DATE)
    assertEquals("desc with \n\nmultiple lines\n", rm.description())
    assertThat(rm.parents())
      .containsExactly(Revision(2, repositoryName), Revision(3, repositoryName))
      .inOrder()
  }

  /**
   * Mocks most of gh.findHeadRevisions(). Used by both of the next tests.
   *
   * @param mockRepo the mock repository to use
   */
  @Throws(CommandException::class)
  private fun mockFindHeadRevisions(mockRepo: GitClonedRepository) {
    expectLogCommand(
      mockRepo, LOG_FORMAT_COMMIT_ID, "HEAD"
    ).andReturn("head")
  }

  @Throws(Exception::class) fun testFindNewRevisions_all() {
    val mockRepo = mockClonedRepo(repositoryName)
    mockFindHeadRevisions(mockRepo)
    val db = DummyDb(false, null)

    // Breadth-first search order.
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "head")
      .andReturn(
        METADATA_JOINER.join(
          "head", "uid@google.com", GIT_COMMIT_DATE,
          "parent1 parent2", "description"
        )
      )
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "parent1")
      .andReturn(
        METADATA_JOINER.join(
          "parent1", "uid@google.com", GIT_COMMIT_DATE, "",
          "description"
        )
      )
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "parent2")
      .andReturn(
        METADATA_JOINER.join(
          "parent2", "uid@google.com", GIT_COMMIT_DATE, "",
          "description"
        )
      )
    control.replay()
    val rh = GitRevisionHistory(Suppliers.ofInstance(mockRepo))
    val newRevisions =
      rh.findRevisions(
        null, RepositoryEquivalenceMatcher("mockRepo", db),
        BRANCHED
      )
        .revisionsSinceEquivalence
        .breadthFirstHistory
    assertThat(newRevisions).hasSize(3)
    assertEquals(repositoryName, newRevisions[0].repositoryName)
    assertEquals("head", newRevisions[0].revId)
    assertEquals(repositoryName, newRevisions[1].repositoryName)
    assertEquals("parent1", newRevisions[1].revId)
    assertEquals(repositoryName, newRevisions[2].repositoryName)
    assertEquals("parent2", newRevisions[2].revId)
    control.verify()
  }

  @Throws(Exception::class) fun testFindNewRevisions_pruned() {
    val mockRepo = mockClonedRepo(repositoryName)
    mockFindHeadRevisions(mockRepo)

    // Create a fake db that has equivalences for parent1, so that
    // parent1 isn't included in the output.
    val db: DummyDb =
      object : DummyDb(true, null) {
        override fun findEquivalences(
          revision: Revision,
          otherRepository: String
        ): Set<Revision> {
          return if (revision.revId() == "parent1") {
            super.findEquivalences(revision, otherRepository)
          } else {
            ImmutableSet.of()
          }
        }
      }

    // Breadth-first search order.
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "head")
      .andReturn(
        METADATA_JOINER.join(
          "head", "uid@google.com", GIT_COMMIT_DATE,
          "parent1 parent2", "description"
        )
      )
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "parent2")
      .andReturn(
        METADATA_JOINER.join(
          "parent2", "uid@google.com", GIT_COMMIT_DATE, "",
          "description"
        )
      )
    control.replay()
    val rh = GitRevisionHistory(Suppliers.ofInstance(mockRepo))
    val newRevisions =
      rh.findRevisions(
        null, RepositoryEquivalenceMatcher("mockRepo", db),
        BRANCHED
      )
        .revisionsSinceEquivalence
        .breadthFirstHistory

    // parent1 should not be traversed.
    assertThat(newRevisions).hasSize(2)
    assertEquals("head", newRevisions[0].revId)
    assertEquals("parent2", newRevisions[1].revId)
    control.verify()
  }

  /*
   * A database that holds the following equivalences:
   * repo1{1002} == repo2{2}
   */
  private val testDb1 = ("{\"equivalences\":[" +
    "{\"rev1\": {\"rev_id\":\"1002\",\"repository_name\":\"repo1\"}," +
    " \"rev2\": {\"rev_id\":\"2\",\"repository_name\":\"repo2\"}}]}")

  /**
   * A test for finding the last equivalence for the following history starting
   * with repo2{4}:<pre>
   * _____
   * |     |
   * |  4  |
   * |_____|
   * |  \
   * |   \
   * |    \
   * __|__   \_____
   * |     |  |     |
   * |  3a |  | 3b  |
   * |_____|  |_____|
   * |     /
   * |    /
   * |   /
   * ____                       __|__/
   * |    |                     |     |
   * |1002|=====================|  2  |
   * |____|                     |_____|
   *
   * repo1                      repo2
  </pre> *
   *
   * @throws Exception
   */
  @Throws(Exception::class) fun testFindLastEquivalence() {
    val mockRepo = mockClonedRepo("repo2")
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "4")
      .andReturn(
        METADATA_JOINER.join(
          "4", "author", GIT_COMMIT_DATE, "3a 3b", "description"
        )
      )
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "3a")
      .andReturn(
        METADATA_JOINER.join(
          "3a", "author", GIT_COMMIT_DATE, "2", "description"
        )
      )
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "3b")
      .andReturn(
        METADATA_JOINER.join(
          "3b", "author", GIT_COMMIT_DATE, "2", "description"
        )
      )
    control.replay()
    val database = FileDb(null, provideMoshi()
      .adapter(DbStorage::class.java)
      .fromJson(testDb1), null)
    val history = GitRevisionHistory(Suppliers.ofInstance(mockRepo))
    val result =
      history.findRevisions(
        Revision(4, "repo2"),
        RepositoryEquivalenceMatcher("repo1", database),
        BRANCHED
      )
    val expectedEq =
      RepositoryEquivalence(
        Revision(1002, "repo1"),
        Revision(2, "repo2")
      )
    assertThat(result.equivalences).hasSize(1)
    assertEquals(expectedEq, result.equivalences[0])
    control.verify()
  }

  /*
   * A database that holds the following equivalences:
   * repo1{1005} == repo2{5}
   */
  private val testDb2 = ("{\"equivalences\":[" +
    "{\"rev1\": {\"rev_id\":\"1005\",\"repository_name\":\"repo1\"}," +
    " \"rev2\": {\"rev_id\":\"5\",\"repository_name\":\"repo2\"}}]}")

  /**
   * A test for finding the last equivalence for the following history starting
   * with repo2{4}:<pre>
   * ____                       _____
   * |    |                     |     |
   * |1005|=====================|  5  |
   * |____|                     |_____|
   * |
   * |
   * |
   * __|__
   * |     |
   * |  4  |
   * |_____|
   * |  \
   * |   \
   * |    \
   * __|__   \_____
   * |     |  |     |
   * |  3a |  | 3b  |
   * |_____|  |_____|
   * |     /
   * |    /
   * |   /
   * __|__/
   * |     |
   * |  2  |
   * |_____|
   *
   * repo1                      repo2
  </pre> *
   *
   * @throws Exception
   */
  @Throws(Exception::class) fun testFindLastEquivalenceNull() {
    val mockRepo = mockClonedRepo("repo2")
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "4")
      .andReturn(
        METADATA_JOINER.join(
          "4", "author", GIT_COMMIT_DATE, "3a 3b", "description"
        )
      )
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "3a")
      .andReturn(
        METADATA_JOINER.join(
          "3a", "author", GIT_COMMIT_DATE, "2", "description"
        )
      )
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "3b")
      .andReturn(
        METADATA_JOINER.join(
          "3b", "author", GIT_COMMIT_DATE, "2", "description"
        )
      )
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "2")
      .andReturn(
        METADATA_JOINER.join(
          "2", "author", GIT_COMMIT_DATE, "", "description"
        )
      )
    control.replay()
    val database =
      FileDb(null, provideMoshi().adapter(DbStorage::class.java).fromJson(testDb2), null)
    val history = GitRevisionHistory(Suppliers.ofInstance(mockRepo))
    val result =
      history.findRevisions(
        Revision(4, "repo2"),
        RepositoryEquivalenceMatcher("repo1", database),
        BRANCHED
      )
    assertThat(result.equivalences).isEmpty()
    control.verify()
  }

  /**
   * A test for finding the last equivalence for the following history starting
   * with repo2{4} and *only searching linear history* instead of following multi-parent
   * commits:<pre>
   *
   * _____
   * |     |
   * |  4  |
   * |_____|
   * |  \
   * |   \
   * |    \
   * __|__   \_____
   * |     |  |     |
   * |  3a |  | 3b  |
   * |_____|  |_____|
   * |     /
   * |    /
   * |   /
   * ____                       __|__/
   * |    |                     |     |
   * |1002|=====================|  2  |
   * |____|                     |_____|
   *
   * repo1                      repo2
  </pre> *
   *
   * @throws Exception
   */
  @Throws(Exception::class) fun testFindLastEquivalence_linearSearch() {
    val mockRepo = mockClonedRepo("repo2")
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "4")
      .andReturn(
        METADATA_JOINER.join("4", "author", GIT_COMMIT_DATE, "3a 3b", "description")
      )
    expectLogCommandIgnoringMissing(mockRepo, LOG_FORMAT_ALL_METADATA, "3a")
      .andReturn(
        METADATA_JOINER.join("3a", "author", GIT_COMMIT_DATE, "2", "description")
      )

    // Note revision 3b is <em>not</em> expected here for a linear history search.
    control.replay()
    val database =
      FileDb(null, provideMoshi().adapter(DbStorage::class.java).fromJson(testDb1), null)
    val history = GitRevisionHistory(Suppliers.ofInstance(mockRepo))
    val result =
      history.findRevisions(
        Revision(4, "repo2"),
        RepositoryEquivalenceMatcher("repo1", database),
        LINEAR
      )
    val expectedEq =
      RepositoryEquivalence(
        Revision(1002, "repo1"),
        Revision(2, "repo2")
      )
    assertThat(result.revisionsSinceEquivalence.breadthFirstHistory)
      .containsExactly(Revision(4, "repo2"), Revision("3a", "repo2"))
      .inOrder()
    assertThat(result.equivalences).containsExactly(expectedEq)
    control.verify()
  }
}
