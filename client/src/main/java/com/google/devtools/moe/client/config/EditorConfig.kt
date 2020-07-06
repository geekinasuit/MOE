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
import com.squareup.moshi.Json

/**
 * Configuration for a MOE Editor.
 */
data class EditorConfig(
  val type: EditorType,

  @Json(name = "scrubber_config")
  val scrubberConfig: ScrubberConfig?,

  @Json(name = "command_string")
  val commandString: String?,

  val mappings: Map<String, String> = mapOf(),

  @Json(name = "use_regex")
  val useRegex: Boolean = false

) : ValidatingConfig {
  @Throws(InvalidProject::class)
  override fun validate() {
    InvalidProject.assertNotNull(type, "Missing type in editor")
  }
}
