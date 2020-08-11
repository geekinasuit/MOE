package com.google.devtools.moe.client.config

/**
 * Enum of all known editor implementations.
 *
 * All values are valid JSON editor types.
 */
enum class EditorType {
  identity,
  scrubber,
  patcher,
  shell,
  renamer
}
