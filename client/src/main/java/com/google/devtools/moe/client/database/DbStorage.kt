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
package com.google.devtools.moe.client.database

/**
 * MOE's database, storing all Equivalences and SubmittedMigrations in order from those between
 * lower revisions to those between higher revisions.
 *
 *
 * This class is used for serialization of a database file.
 */
data class DbStorage(
  private val equivalences: MutableList<RepositoryEquivalence> = mutableListOf(),
  private val migrations: MutableList<SubmittedMigration> = mutableListOf()
) {

  fun equivalences(): List<RepositoryEquivalence> = equivalences.toList()

  fun migrations(): List<SubmittedMigration> = migrations.toList()

  fun addEquivalence(e: RepositoryEquivalence) {
    if (!equivalences.contains(e)) {
      equivalences.add(e)
    }
  }

  /**
   * Adds a SubmittedMigration.
   *
   * @return true if the SubmittedMigration was newly added, false if it was already in this Db
   */
  fun addMigration(m: SubmittedMigration): Boolean {
    return !migrations.contains(m) && migrations.add(m)
  }

  /** Returns true if this SubmittedMigration has been recorded in this database.  */
  fun hasMigration(m: SubmittedMigration): Boolean {
    return migrations.contains(m)
  }
}
