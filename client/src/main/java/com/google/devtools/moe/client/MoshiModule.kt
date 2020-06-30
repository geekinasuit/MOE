package com.google.devtools.moe.client

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides

@Module
abstract class MoshiModule {
  @Module companion object {
    private val MOSHI: Moshi = Moshi.Builder()
      // ... add your own JsonAdapters and factories ...
      .add(KotlinJsonAdapterFactory())
      .build()
    @JvmStatic @Provides fun provideMoshi() = MOSHI
  }
}
