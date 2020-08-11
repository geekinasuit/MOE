package com.google.devtools.moe.client.config

import com.google.devtools.moe.client.InvalidProject

/** A thin interface for parsing configurations. */
interface ConfigParser<T> {
  @Throws(InvalidProject::class)
  fun parse(json: String?): T
}
