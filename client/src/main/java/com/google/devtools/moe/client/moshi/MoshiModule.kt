package com.google.devtools.moe.client.moshi

import com.google.devtools.moe.client.config.ConfigParser
import com.google.devtools.moe.client.config.ProjectConfig
import com.google.devtools.moe.client.config.UsernamesConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides

@Module
interface MoshiModule {
  @Binds fun usernamesConfigParser(p: MoshiUsernameConfigParser): ConfigParser<UsernamesConfig>
  @Binds fun projectConfigParser(p: MoshiProjectConfigParser): ConfigParser<ProjectConfig>
  @Module companion object {
    private val MOSHI: Moshi = Moshi.Builder()
      // ... add your own JsonAdapters and factories ...
      .add(KotlinJsonAdapterFactory())
      .build()
    @JvmStatic @Provides fun provideMoshi() = MOSHI
  }
}
