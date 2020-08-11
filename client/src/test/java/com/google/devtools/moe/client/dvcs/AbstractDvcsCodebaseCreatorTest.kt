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
package com.google.devtools.moe.client.dvcs

import com.google.common.base.Suppliers
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.devtools.moe.client.FileSystem
import com.google.devtools.moe.client.codebase.LocalWorkspace
import com.google.devtools.moe.client.config.RepositoryConfig
import com.google.devtools.moe.client.repositories.Revision
import com.google.devtools.moe.client.repositories.RevisionHistory
import java.io.File
import junit.framework.TestCase
import org.easymock.EasyMock

class AbstractDvcsCodebaseCreatorTest : TestCase() {
  private val control = EasyMock.createControl()
  private val mockFS = control.createMock(FileSystem::class.java)
  private val fakeRepoConfig = RepositoryConfig("fake")
  private val mockRepo = control.createMock(LocalWorkspace::class.java)
  private val mockRevHistory = control.createMock(RevisionHistory::class.java)
  private val codebaseCreator: AbstractDvcsCodebaseCreator =
    object : AbstractDvcsCodebaseCreator(
      null,
      mockFS,
      Suppliers.ofInstance(mockRepo),
      mockRevHistory,
      "public"
    ) {
      override fun cloneAtLocalRoot(localroot: String): LocalWorkspace {
        throw UnsupportedOperationException()
      }
    }

  @Throws(Exception::class) override fun setUp() {
    super.setUp()
    EasyMock.expect(mockRepo.config).andReturn(fakeRepoConfig).anyTimes()
    EasyMock.expect(mockRepo.repositoryName).andReturn(MOCK_REPO_NAME)
  }

  @Throws(Exception::class) fun testCreate_noGivenRev() {
    val archiveTempDir = "/tmp/git_archive_mockrepo_head"
    // Short-circuit Utils.filterFilesByPredicate(ignore_files_re).
    EasyMock.expect(mockFS.findFiles(File(archiveTempDir))).andReturn(ImmutableSet.of())
    EasyMock.expect(mockRevHistory.findHighestRevision(null))
      .andReturn(Revision("mock head changeset ID", MOCK_REPO_NAME))
    EasyMock.expect(mockRepo.archiveAtRevision("mock head changeset ID"))
      .andReturn(File(archiveTempDir))
    control.replay()
    val codebase = codebaseCreator.create(emptyMap())
    assertEquals(File(archiveTempDir), codebase.root())
    assertEquals("public", codebase.projectSpace())
    assertEquals("mockrepo", codebase.expression().toString())
    control.verify()
  }

  @Throws(Exception::class) fun testCreate_givenRev() {
    val givenRev = "givenrev"
    val archiveTempDir = "/tmp/git_reclone_mockrepo_head_$givenRev"
    // Short-circuit Utils.filterFilesByPredicate(ignore_files_re).
    EasyMock.expect(mockFS.findFiles(File(archiveTempDir))).andReturn(ImmutableSet.of())
    EasyMock.expect(mockRevHistory.findHighestRevision(givenRev))
      .andReturn(Revision(givenRev, MOCK_REPO_NAME))
    EasyMock.expect(mockRepo.archiveAtRevision(givenRev)).andReturn(File(archiveTempDir))
    control.replay()
    val codebase = codebaseCreator.create(ImmutableMap.of("revision", givenRev))
    assertEquals(File(archiveTempDir), codebase.root())
    assertEquals("public", codebase.projectSpace())
    assertEquals("mockrepo(revision=$givenRev)", codebase.expression().toString())
    control.verify()
  }

  companion object {
    private const val MOCK_REPO_NAME = "mockrepo"
  }
}
