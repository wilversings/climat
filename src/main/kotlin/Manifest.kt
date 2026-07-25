package com.climat

const val MANIFEST_TEXT = """
climat(version v: flag) {

  act js {{
    if (JSON.parse(params.get("version"))) {
      console.log(
        require(
          `${"$"}{require('child_process').execSync('npm root -g').toString().trim()}/climat/package.json`
        ).version
      )
    }
  }}

  ""${'"'}
  Executes the command using the toolchain present at the provided path
  @param pathToManifest the path to the toolchain manifest
  ""${'"'}
  @allow-unmatched
  sub exec(pathToManifest p: arg, skipValidation: flag) {
    act js {{ climat.exec(params.get("pathToManifest"), params.get("__UNMATCHED"), params.get("skipValidation")) }}
  }

  ""${'"'}
  Executes the command using the toolchain from somewhere up the directory hierarchy
  ""${'"'}
  @allow-unmatched
  sub run {
    act js {{ climat.run(params.get("__UNMATCHED")) }}
  }

  ""${'"'}
  Executes the command using the globally installed toolchain
  @param name the name of the globally installed toolchain
  @param command the command
  ""${'"'}
  @allow-unmatched
  sub runGlobal(name n: arg, shellPath: arg?) {
    act js {{ climat.runGlobal(params.get("name"), params.get("__UNMATCHED"), params.get("shellPath")) }}
  }

  ""${'"'}
  Validates the toolchain present at the provided path
  @param pathToManifest the path to toolchain manifest
  ""${'"'}
  sub validate(pathToManifest p: arg) {
    act js {{ climat.validate(params.get("pathToManifest")) }}
  }

  ""${'"'}
  Uninstalls the globally installed toolchain
  @param name the name of the toolchain to uninstall
  ""${'"'}
  sub uninstall(name n: arg) {
    act js {{ climat.uninstall(params.get("name")) }}
  }

  ""${'"'}
  Globally installs the toolchain present at the provided path
  @param pathToManifest the path to toolchain manifest
  ""${'"'}
  @alias(i)
  sub install(pathToManifest p: arg) {
    act js {{ climat.install(params.get("pathToManifest")) }}
  }

  ""${'"'}
  Outputs a list of the globally installed toolchains
  ""${'"'}
  @aliases(ls l)
  sub list {
    act js {{ climat.list() }}
  }

  ""${'"'}
  Cleans all the installed toolchains and all the clutter left behid by Climat
  ""${'"'}
  sub purge {
    act js {{ climat.purge() }}
  }
}"""
