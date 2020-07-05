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

import com.google.common.collect.ImmutableSet
import com.google.devtools.moe.client.CommandRunner.CommandException
import com.google.devtools.moe.client.FileSystem
import com.google.devtools.moe.client.Ui
import com.google.devtools.moe.client.codebase.Codebase
import com.google.devtools.moe.client.codebase.expressions.RepositoryExpression
import com.google.devtools.moe.client.config.RepositoryConfig
import java.io.File
import junit.framework.TestCase
import org.easymock.EasyMock

/** Test GitWriter by expect()ing file system calls and git commands to add/remove files.  */
class GitWriterTest : TestCase() {
  private val control = EasyMock.createControl()
  private val mockFs = control.createMock(FileSystem::class.java)
  private val codebaseRoot = File("/codebase")
  private val writerRoot = File("/writer")
  private val projectSpace = "public"
  private val cExp = RepositoryExpression(projectSpace)
  private val codebase = Codebase.create(codebaseRoot, projectSpace, cExp)
  private val mockRevClone = control.createMock(GitClonedRepository::class.java)
  private val fakeRepoConfig = RepositoryConfig(type = "fake", projectSpace = projectSpace)
  private val ui = Ui(System.err)

  /* Helper methods */
  @Throws(CommandException::class)
  private fun expectGitCmd(vararg args: String) {
    EasyMock.expect(mockRevClone.runGitCommand(*args)).andReturn("" /* stdout */)
  }

  /* End helper methods */
  @Throws(Exception::class) override fun setUp() {
    super.setUp()
    EasyMock.expect(mockRevClone.localTempDir).andReturn(writerRoot).anyTimes()
  }

  @Throws(Exception::class) fun testPutCodebase_emptyCodebase() {
    // Define the files in the codebase and in the writer (git repo).
    EasyMock.expect(mockRevClone.config).andReturn(fakeRepoConfig).anyTimes()
    EasyMock.expect(mockFs.findFiles(codebaseRoot)).andReturn(ImmutableSet.of())
    EasyMock.expect(mockFs.findFiles(writerRoot))
      .andReturn(
        ImmutableSet.of( // Doesn't seem to matter that much what we return here, other than .git.
          File(writerRoot, ".git/branches")
        )
      )

    // Expect no other mockFs calls from GitWriter.putFile().
    control.replay()
    val w =
      GitWriter(mockRevClone, mockFs, ui)
    val dr = w.putCodebase(codebase, null)
    control.verify()
    assertEquals(writerRoot.absolutePath, dr.location)
  }

  @Throws(Exception::class) fun testPutCodebase_addFile() {
    EasyMock.expect(mockRevClone.config).andReturn(fakeRepoConfig).anyTimes()
    EasyMock.expect(mockFs.findFiles(codebaseRoot))
      .andReturn(ImmutableSet.of(File(codebaseRoot, "file1")))
    EasyMock.expect(mockFs.findFiles(writerRoot)).andReturn(ImmutableSet.of())
    EasyMock.expect(mockFs.exists(File(codebaseRoot, "file1"))).andReturn(true)
    EasyMock.expect(mockFs.exists(File(writerRoot, "file1"))).andReturn(false)
    mockFs.makeDirsForFile(File(writerRoot, "file1"))
    mockFs.copyFile(File(codebaseRoot, "file1"), File(writerRoot, "file1"))
    expectGitCmd("add", "-f", "file1")
    control.replay()
    val w =
      GitWriter(mockRevClone, mockFs, ui)
    w.putCodebase(codebase, null)
    control.verify()
  }

  @Throws(Exception::class) fun testPutCodebase_editFile() {
    EasyMock.expect(mockRevClone.config).andReturn(fakeRepoConfig).anyTimes()
    EasyMock.expect(mockFs.findFiles(codebaseRoot))
      .andReturn(ImmutableSet.of(File(codebaseRoot, "file1")))
    EasyMock.expect(mockFs.findFiles(writerRoot))
      .andReturn(ImmutableSet.of(File(writerRoot, "file1")))
    EasyMock.expect(mockFs.exists(File(codebaseRoot, "file1"))).andReturn(true)
    EasyMock.expect(mockFs.exists(File(writerRoot, "file1"))).andReturn(true)
    mockFs.makeDirsForFile(File(writerRoot, "file1"))
    mockFs.copyFile(File(codebaseRoot, "file1"), File(writerRoot, "file1"))
    expectGitCmd("add", "-f", "file1")
    control.replay()
    val w = GitWriter(mockRevClone, mockFs, ui)
    w.putCodebase(codebase, null)
    control.verify()
  }

  @Throws(Exception::class) fun testPutCodebase_removeFile() {
    EasyMock.expect(mockRevClone.config).andReturn(fakeRepoConfig).anyTimes()
    EasyMock.expect(mockFs.findFiles(codebaseRoot)).andReturn(ImmutableSet.of())
    EasyMock.expect(mockFs.findFiles(writerRoot))
      .andReturn(ImmutableSet.of(File(writerRoot, "file1")))
    EasyMock.expect(mockFs.exists(File(codebaseRoot, "file1"))).andReturn(false)
    EasyMock.expect(mockFs.exists(File(writerRoot, "file1"))).andReturn(true)
    expectGitCmd("rm", "file1")
    control.replay()
    val w = GitWriter(mockRevClone, mockFs, ui)
    w.putCodebase(codebase, null)
    control.verify()
  }

  @Throws(Exception::class) fun testPutCodebase_ignoreFilesRes() {
    EasyMock.expect(mockRevClone.config)
      .andReturn(
        fakeRepoConfig.copy(ignoreFilePatterns = listOf("^.*ignored_\\w+\\.txt$"))
      ).anyTimes()
    EasyMock.expect(mockFs.findFiles(codebaseRoot)).andReturn(ImmutableSet.of())
    EasyMock.expect(mockFs.findFiles(writerRoot))
      .andReturn(
        ImmutableSet.of(
          File(writerRoot, ".git/branches"),
          File(writerRoot, "not_really_ignored_dir/file1"),
          File(writerRoot, "included_dir/ignored_file.txt")
        )
      )
    EasyMock.expect(mockFs.exists(File(codebaseRoot, "not_really_ignored_dir/file1")))
      .andReturn(false)
    EasyMock.expect(mockFs.exists(File(writerRoot, "not_really_ignored_dir/file1")))
      .andReturn(true)
    expectGitCmd("rm", "not_really_ignored_dir/file1")
    control.replay()
    val w = GitWriter(mockRevClone, mockFs, ui)
    val dr = w.putCodebase(codebase, null)
    control.verify()
    assertEquals(writerRoot.absolutePath, dr.location)
  }
}
