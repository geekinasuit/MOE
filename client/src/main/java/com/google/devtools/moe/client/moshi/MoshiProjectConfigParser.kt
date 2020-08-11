package com.google.devtools.moe.client.moshi

import com.google.devtools.moe.client.InvalidProject
import com.google.devtools.moe.client.config.ConfigParser
import com.google.devtools.moe.client.config.ProjectConfig
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import dagger.Reusable
import java.io.IOException
import javax.inject.Inject

@Reusable
class MoshiProjectConfigParser @Inject constructor(val moshi: Moshi) : ConfigParser<ProjectConfig> {
  @Throws(InvalidProject::class)
  override fun parse(json: String?): ProjectConfig {
    InvalidProject.assertNotNull(json, "No MOE project config was specified.")
    return try {
      val adapter = moshi.adapter(ProjectConfig::class.java).failOnUnknown().lenient()
      val config = adapter.fromJson(json!!)
      InvalidProject.assertNotNull(config, "Could not parse MOE config")
      config!!.also { it.validate() }
    } catch (e: JsonDataException) {
      throw InvalidProject("Could not parse MOE config: " + e.message)
    } catch (e: IOException) {
      throw InvalidProject("Could not parse MOE config: " + e.message)
    }
  }
}
