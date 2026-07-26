const webpack = require('webpack');

const CopyPlugin = require("copy-webpack-plugin");
const BannerPlugin = webpack.BannerPlugin

config.target = 'node'

// Keep Node's real __dirname (not a webpack mock) — SpawnMicroshell resolves the bundled
// microshell binaries relative to the emitted bundle.
config.node = Object.assign({}, config.node, {__dirname: false, __filename: false})

config.plugins.push(
    new CopyPlugin({
        patterns: [
            "package.json", "LICENSE.md", "README.md",
            // Staged by the `copyMicroshellBinaries` Gradle task. Executable bits must survive.
            {from: "msh", to: "msh", noErrorOnMissing: true, info: {minimized: true}},
        ]
    }),

    new BannerPlugin({
        banner: "#!/usr/bin/env -S node --no-warnings",
        raw: true
    })
)