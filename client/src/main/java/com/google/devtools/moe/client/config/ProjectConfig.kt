package com.google.devtools.moe.client.config

import com.google.common.base.Strings
import com.google.devtools.moe.client.InvalidProject
import com.google.devtools.moe.client.MoeProblem
import com.squareup.moshi.Json

class ProjectConfig(
  val name: String,

  @Json(name = "database_uri")
  val databaseUri: String? = null,

  /** The set of configured editors for this project  */
  val editors: Map<String, EditorConfig> = mapOf(),

  /** The set of migration configurations that have been set for this project.  */
  val migrations: List<MigrationConfig> = listOf(),

  /** The configured repositories this project is aware of.  */
  val repositories: Map<String, RepositoryConfig> = mapOf(),

  /** The set of translations that have been configured for this project.  */
  val translators: List<TranslatorConfig> = listOf()

) {

  companion object {
    @JvmStatic fun createNoop(name: String) = ProjectConfig(name = name)
  }

  /** Returns the [RepositoryConfig] in this config with the given name. */
  @Throws(MoeProblem::class)
  fun getRepositoryConfig(name: String): RepositoryConfig {
    return repositories[name] ?: throw MoeProblem(
      "No such repository '%s' in the config. Found: %s",
      name, repositories.keys.sorted()
    )
  }

  /** Returns a configuration from one repository to another, if any is configured. */
  fun findTranslatorFrom(from: String, to: String): TranslatorConfig? {
    val fromProjectSpace = getRepositoryConfig(from).projectSpace
    val toProjectSpace = getRepositoryConfig(to).projectSpace
    return translators.firstOrNull {
      it.fromProjectSpace == fromProjectSpace && it.toProjectSpace == toProjectSpace
    }
  }

  fun findScrubberConfig(from: String, to: String) = findTranslatorFrom(from, to)?.scrubber()

  @Throws(InvalidProject::class) fun validate() {
    InvalidProject.assertFalse(Strings.isNullOrEmpty(name), "Must specify a name")
    InvalidProject.assertFalse(repositories.isEmpty(), "Must specify repositories")
    val configs = sequenceOf<ValidatingConfig>() +
      repositories.values +
      editors.values +
      translators +
      migrations
    configs.forEach { it.validate() }
  }
}
