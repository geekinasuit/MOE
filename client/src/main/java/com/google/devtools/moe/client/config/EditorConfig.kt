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
package com.google.devtools.moe.client.config

import com.google.devtools.moe.client.InvalidProject
import com.google.devtools.moe.client.config.EditorType.renamer
import com.google.devtools.moe.client.config.EditorType.scrubber
import com.google.devtools.moe.client.config.EditorType.shell
import com.squareup.moshi.Json

/**
 * Configuration for a MOE Editor.
 */
data class EditorConfig(
  val type: EditorType,

  @Json(name = "scrubber_config")
  val scrubberConfig: ScrubberConfig? = null,

  @Json(name = "command_string")
  val commandString: String? = null,

  val mappings: Map<String, String> = mapOf(),

  @Json(name = "use_regex")
  val useRegex: Boolean = false

) : ValidatingConfig {

  val regexMappings: Map<Regex, String> by lazy {
    mappings.map { (p, r) -> p.toRegex() to r }.toMap()
  }

  @Throws(InvalidProject::class)
  override fun validate() {
    InvalidProject.assertNotNull(type, "Missing type in editor")
    when (type) {
      scrubber -> InvalidProject.assertNotNull(
        scrubberConfig,
        "Failed to specify a \"scrubber_config\" in scrubbing editor."
      )
      shell -> InvalidProject.assertNotNull(
        commandString,
        "Failed to specify a \"command_string\" in shell editor."
      )
      renamer -> InvalidProject.assertNotNull(
        mappings,
        "Failed to specify \"mappings\" in renamer editor."
      )
    }
  }
}
