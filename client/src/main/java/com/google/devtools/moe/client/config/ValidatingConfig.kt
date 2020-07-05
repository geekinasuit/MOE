package com.google.devtools.moe.client.config

import com.google.devtools.moe.client.InvalidProject

/** A common interface for configs which partcipate in validation. */
interface ValidatingConfig {
  @Throws(InvalidProject::class)
  fun validate()
}
