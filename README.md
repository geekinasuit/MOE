# Moe
*Make Open Easy*

[![LICENSE](https://img.shields.io/badge/license-Apache-blue.svg)](https://github.com/geekinasuit/MOE/blob/master/LICENSE)
[![CI](https://github.com/geekinasuit/MOE/workflows/CI/badge.svg)](https://github.com/geekinasuit/MOE/actions?query=workflow%3ACI)
[![GitHub Issues](https://img.shields.io/github/issues/geekinasuit/MOE.svg)](https://github.com/geekinasuit/MOE/issues)
[![GitHub Pull Requests](https://img.shields.io/github/issues-pr/geekinasuit/MOE.svg)](https://github.com/geekinasuit/MOE/pulls)

> Note: This is a separately maintained fork of [Google's MOE](http://github.com/google/moe).
> Given that Google's MOE is now unmaintained, little to no effort will be made to
> keep in sync, and I expect the code to stray from the original substantially in detail.

## Introduction

MOE is a system for synchronizing, translating, and scrubbing source code
repositories.  Often, a project needs to exist in two forms, typically because
it is released in open-source, which may use a different build system, only
be a subset of the wider project, etc.  Maintaining code in two repositories
is burdensome. MOE allows users to:

  * synchronize (in either or both directions) between two source code
    repositories
  * use different types of repositories (svn, hg, git) in combinations
  * maintain "scrubbed" content in an internal or private repository.
  * transform project paths to support different layouts/structures in
    different repositories
  * propagate or hide individual commits, commit-authorship, and other
    metadata between repositories while syncing.

## Project Status

MOE was created around 2011, but has not had a lot of love. Google teams that
maintain open-source releases (guava, dagger, auto, etc.) use it regularly,
so we dusted it off to share fixes, improvements, and help folks who use it
outside of Google.

The project is currently undergoing a fair bit of re-factoring and needs a
documentation update, which is forthcoming.

## Usage

### Building MOE

   1. Install [Bazelisk](http://github.com/bazelbuild/bazelisk)
   2. Checkout the Java-MOE source `git clone git@github.com:google/MOE.git`
   3. In the top level directory that contains the WORKSPACE file, run:
      - `bazel build //client:moe`
   4. The moe client binary should be created at `bazel-bin/client/moe`
   5. (optionally) install the client somewhere in your `$PATH`

### Running MOE

Once you have the `moe` binary, you should be able to simply run:
`moe <arguments for MOE>`

### Configuring MOE

To configure your project for moe, [check out the wiki](https://github.com/geekinasuit/moe/wiki)

## Contributing

Contributing to MOE is subject to the guidelines in the CONTRIBUTING.md file

## License

```
  Copyright 2011 The Moe Authors. All Rights Reserved.
  Copyright 2011 Google, Inc. All Rights Reserved.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
```


