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

/** Configuration for a MOE migration. */
data class MigrationConfig(
  val name: String,
  val from: String,
  val to: String,
  @Json(name = "separate_revisions")
  val separateRevisions: Boolean = false,
  @Json(name = "metadata_scrubber")
  val metadataScrubberConfig: MetadataScrubberConfig? = null
) : ValidatingConfig {

  fun copyWithFromRepository(alternate: String) = copy(from = alternate)

  @Throws(InvalidProject::class) override fun validate() {
    InvalidProject.assertNotEmpty(name, "Missing name in migration")
    InvalidProject.assertNotEmpty(from, "Missing from_repository in migration")
    InvalidProject.assertNotEmpty(to, "Missing to_repository in migration")
  }
}
