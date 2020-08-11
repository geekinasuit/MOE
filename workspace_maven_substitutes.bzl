_DAGGER = """
java_library(
    name = "dagger",
    exports = [
        ":dagger-api",
        "@maven//javax/inject:javax_inject",
    ],
    exported_plugins = [":plugin"],
    visibility = ["//visibility:public"],
)

raw_jvm_import(
    name = "dagger-api",
    jar = "@com_google_dagger_dagger//maven",
    visibility = ["//visibility:public"],
    deps = [
        "@maven//javax/inject:javax_inject",
    ],
)

java_plugin(
    name = "plugin",
    processor_class = "dagger.internal.codegen.ComponentProcessor",
    generates_api = True,
    deps = [":dagger-compiler"],
)
"""

_AUTO_FACTORY = """
java_library(
    name = "factory",
    exports = [
        "@maven//com/google/auto/factory:auto-factory",
    ],
    exported_plugins = [":plugin"],
    neverlink = True, # this is only needed at compile-time, for code-gen.
    visibility = ["//visibility:public"],
)

raw_jvm_import(
    name = "auto-factory",
    jar = "@com_google_auto_factory_auto_factory//maven",
    visibility = ["//visibility:public"],
)

java_plugin(
    name = "plugin",
    processor_class = "com.google.auto.factory.processor.AutoFactoryProcessor",
    generates_api = True,
    deps = [":auto-factory"],
)
"""

_AUTO_VALUE = """
java_library(
    name = "value",
    exports = [
        ":auto-value-annotations",
    ],
    exported_plugins = [":plugin"],
    visibility = ["//visibility:public"],
)

raw_jvm_import(
    name = "auto-value",
    jar = "@com_google_auto_value_auto_value//maven",
    visibility = ["@maven//com/ryanharter/auto/value:__subpackages__"],
)

java_plugin(
    name = "plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    generates_api = True,
    deps = [
        ":auto-value",
        "@maven//com/google/auto:auto-common",
    ],
)
"""

snippets = struct(
    AUTO_VALUE = _AUTO_VALUE,
    AUTO_FACTORY = _AUTO_FACTORY,
    DAGGER = _DAGGER,
)
