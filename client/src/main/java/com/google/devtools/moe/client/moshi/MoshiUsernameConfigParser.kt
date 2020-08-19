package com.google.devtools.moe.client.moshi

import com.google.devtools.moe.client.InvalidProject
import com.google.devtools.moe.client.config.ConfigParser
import com.google.devtools.moe.client.config.UsernamesConfig
import com.squareup.moshi.Moshi
import dagger.Reusable
import java.io.IOException
import javax.inject.Inject

@Reusable
class MoshiUsernameConfigParser @Inject constructor(
  val moshi: Moshi
) : ConfigParser<UsernamesConfig> {
  @Throws(InvalidProject::class)
  override fun parse(json: String?): UsernamesConfig {
    InvalidProject.assertNotNull(json, "No MOE project config was specified.")
    return try {
      val config = moshi.adapter(UsernamesConfig::class.java).failOnUnknown().fromJson(json!!)
      InvalidProject.assertNotNull(config, "Could not parse MOE config - no output from parser.")
      return config!!
    } catch (e: IOException) {
      throw InvalidProject(e, "Could not parse MOE config: " + e.message)
    }
  }
}
