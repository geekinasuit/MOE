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

/** Configuration for a MOE Translator */
class TranslatorConfig(
  @Json(name = "from_project_space")
  val fromProjectSpace: String,
  @Json(name = "to_project_space")
  val toProjectSpace: String,
  val steps: List<StepConfig> = listOf(),
  @Json(name = "is_inverse")
  val isInverse: Boolean = false
) : ValidatingConfig {

  // TODO(cgruber) Audit this logic. It always took the first scrubber config - make sure that's ok.
  fun scrubber(): ScrubberConfig? =
    steps.map { it.editorConfig }.firstOrNull { it.type == EditorType.scrubber }?.scrubberConfig

  fun scrubbers(): List<ScrubberConfig> {
    return steps.asSequence()
        .map(StepConfig::editorConfig)
        .filter { ec: EditorConfig -> ec.type == EditorType.scrubber }
        .mapNotNull { obj: EditorConfig -> obj.scrubberConfig }
        .toMutableList()
  }

  @Throws(InvalidProject::class)
  override fun validate() {
    InvalidProject.assertNotEmpty(fromProjectSpace, "Translator requires from_project_space")
    InvalidProject.assertNotEmpty(toProjectSpace, "Translator requires to_project_space")
    if (isInverse) {
      InvalidProject.assertTrue(steps.isEmpty(), "Inverse translator can't have steps")
    } else {
      InvalidProject.assertTrue(steps.isNotEmpty(), "Translator requires steps")
      steps.forEach { it.validate() }
    }
  }
}
