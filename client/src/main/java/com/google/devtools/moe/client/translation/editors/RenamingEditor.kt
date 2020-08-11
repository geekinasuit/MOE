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
package com.google.devtools.moe.client.translation.editors

import com.google.auto.factory.AutoFactory
import com.google.auto.factory.Provided
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.CharMatcher
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.devtools.moe.client.FileSystem
import com.google.devtools.moe.client.InvalidProject
import com.google.devtools.moe.client.MoeProblem
import com.google.devtools.moe.client.Utils
import com.google.devtools.moe.client.codebase.Codebase
import com.google.devtools.moe.client.config.EditorConfig
import com.google.devtools.moe.client.config.EditorType.renamer
import com.google.devtools.moe.client.translation.editors.Editor.Factory
import java.io.File
import java.io.IOException

/** The renaming editor reorganizes the project's hierarchy.  */
@AutoFactory(
  implementing = [Factory::class]
)
class RenamingEditor internal constructor(
  @param:Provided private val filesystem: FileSystem,
  private val editorName: String,
  private val config: EditorConfig
) : Editor, InverseEditor {

  /**
   * Returns a description of what this editor will do.
   */
  override fun getDescription(): String {
    return "rename step $editorName"
  }

  @Throws(InvalidProject::class)
  override fun validateInversion(): InverseEditor {
    if (config.useRegex) {
      throw InvalidProject("Editor type $renamer is not reversable if use_regex=true")
    }
    return this
  }

  /**
   * Recursively copies files from src to dest, changing the filenames as specified
   * in mappings.
   *
   * @param srcFile the absolute path of a file to rename and copy or a dir to crawl
   * @param srcFolder the absolute root of the from folder being crawled
   * @param destFolder the absolute root of the to folder receiving renamed files
   */
  @VisibleForTesting
  @Throws(IOException::class)
  fun copyDirectoryAndRename(srcFile: File, srcFolder: File, destFolder: File?) {
    if (filesystem.isDirectory(srcFile)) {
      val files = filesystem.listFiles(srcFile)
      for (subFile in files) {
        copyDirectoryAndRename(subFile, srcFolder, destFolder)
      }
    } else {
      // "/srcFolder/path/to/file" -> "path/to/file"
      val relativePath = srcFolder.toURI().relativize(srcFile.toURI()).path
      val renamedFile = File(destFolder, renameFile(relativePath))
      filesystem.makeDirsForFile(renamedFile)
      filesystem.copyFile(srcFile, renamedFile)
    }
  }

  /**
   * Returns the filename according to the rules in mappings.
   *
   * @param path the filename to be renamed, relative to the root of the codebase
   *
   * @return the new relative filename
   * @throws MoeProblem if a mapping for inputFilename could not be found
   */
  fun renameFile(path: String): String {
    if (config.useRegex) {
      for ((regex, replacement) in config.regexMappings.entries) {
        val foo = regex.find(path)
        val renamed = regex.replaceFirst(path, replacement)
        if (renamed != path) return FILE_SEP_CHAR_MATCHER.trimLeadingFrom(renamed)
      }
    } else {
      for ((prefix, replacement) in config.mappings.entries) {
        val renamed = path.replaceFirst(prefix, replacement)
        if (renamed != path) return FILE_SEP_CHAR_MATCHER.trimLeadingFrom(renamed)
      }
    }
    throw MoeProblem(
      "Cannot find a rename mapping that covers file $path. " +
        "Every file needs an applicable renaming rule."
    )
  }

  /**
   * Copies the input Codebase's contents, renaming the files according to this.mappings and returns
   * a new Codebase with the results.
   *
   * @param input the Codebase to edit
   * @param options a map containing any command line options such as a specific revision
   */
  override fun edit(
    input: Codebase,
    options: Map<String, String>
  ): Codebase {
    val tempDir = filesystem.getTemporaryDirectory("rename_run_")
    try {
      copyDirectoryAndRename(
        input.root()
          .absoluteFile,
        input.root()
          .absoluteFile,
        tempDir.absoluteFile
      )
    } catch (e: IOException) {
      throw MoeProblem(e, "Failed to copy %s to %s", input.root(), tempDir)
    }
    return Codebase.create(
      tempDir, input.projectSpace(), input.expression()
    )
  }

  override fun inverseEdit(
    input: Codebase,
    referenceFrom: Codebase?,
    referenceTo: Codebase,
    options: Map<String, String>
  ): Codebase {
    val tempDir = filesystem.getTemporaryDirectory("inverse_rename_run_")
    inverseRenameAndCopy(input, tempDir, referenceTo)
    return Codebase.create(
      tempDir, referenceTo.projectSpace(), referenceTo.expression()
    )
  }

  private fun inverseRenameAndCopy(
    input: Codebase,
    destination: File,
    reference: Codebase
  ) {
    val renamedFilenames =
      Utils.makeFilenamesRelative(filesystem.findFiles(input.root()), input.root())
    val renamedToReferenceMap = makeRenamedToReferenceMap(
      Utils.makeFilenamesRelative(filesystem.findFiles(reference.root()), reference.root())
    )
    for (renamedFilename in renamedFilenames) {
      val inverseRenamedFilename = inverseRename(renamedFilename, renamedToReferenceMap)
      copyFile(renamedFilename, inverseRenamedFilename, input.root(), destination)
    }
  }

  private fun copyFile(
    inputFilename: String,
    destFilename: String,
    inputRoot: File,
    destRoot: File
  ) {
    val inputFile = File(inputRoot, inputFilename)
    val destFile = File(destRoot, destFilename)
    try {
      filesystem.makeDirsForFile(destFile)
      filesystem.copyFile(inputFile, destFile)
    } catch (e: IOException) {
      throw MoeProblem(e, "%s", e.message)
    }
  }

  /**
   * Walks backwards through the dir prefixes of renamedFilename looking for a match in
   * renamedToReferenceMap.
   */
  private fun inverseRename(
    renamedFilename: String,
    renamedToReferenceMap: Map<String, String>
  ): String {
    val renamedAllParts =
      FILE_SEP_SPLITTER.splitToList(
        renamedFilename
      )
    for (i in renamedAllParts.size downTo 1) {
      val renamedParts =
        FILE_SEP_JOINER.join(
          renamedAllParts.subList(0, i)
        )
      val partsToSubstitute = renamedToReferenceMap[renamedParts]
      if (partsToSubstitute != null) {
        return renamedFilename.replace(renamedParts, partsToSubstitute)
      }
    }
    // No inverse renaming found.
    return renamedFilename
  }

  /**
   * Returns mappings (renamed path, original/reference path) for all paths in the renamed/input
   * Codebase.
   */
  private fun makeRenamedToReferenceMap(referenceFilenames: Set<String>): Map<String, String> {
    // Use a HashMap instead of ImmutableMap.Builder because we may put the same key (e.g. a
    // high-level dir) multiple times. We may want to complain if trying to put a new value for a
    // dir (i.e. if two different reference paths are renamed to the same path), but we don't now.
    val tmpPathMap = Maps.newHashMap<String, String>()
    for (refFilename in referenceFilenames) {
      val renamed = renameFile(refFilename)
      val renamedPathParts = Lists.newLinkedList(
        FILE_SEP_SPLITTER.split(
          renamed
        )
      )
      val refPathParts = Lists.newLinkedList(
        FILE_SEP_SPLITTER.split(
          refFilename
        )
      )

      // Put a mapping for each directory prefix of the renaming, stopping at the root of either
      // path. For example, a renaming a/b/c/file -> x/y/file creates mappings for each dir prefix:
      // - x/y/file -> a/b/c/file
      // - x/y -> a/b/c
      // - x -> a/b
      while (!renamedPathParts.isEmpty() && !refPathParts.isEmpty()) {
        tmpPathMap[FILE_SEP_JOINER.join(
          renamedPathParts
        )] = FILE_SEP_JOINER.join(
          refPathParts
        )
        renamedPathParts.removeLast()
        refPathParts.removeLast()
      }
    }
    return ImmutableMap.copyOf(tmpPathMap)
  }

  companion object {
    private val FILE_SEP_CHAR_MATCHER = CharMatcher.`is`(File.separatorChar)
    private val FILE_SEP_JOINER = Joiner.on(File.separator)
    private val FILE_SEP_SPLITTER = Splitter.on(File.separator)
  }

  init {
    if (config.mappings.isEmpty()) {
      throw MoeProblem("No mappings object found in the config for editor %s", editorName)
    }
  }
}
