# Qlty for JetBrains

[Qlty](https://qlty.sh) is the fastest and easiest way to integrate multi-language code quality checks into your
development process and GitHub pull request workflows.

This plugin brings Qlty's code quality analysis directly into JetBrains IDEs.

## Features

- **Inline diagnostics** — Issues from `qlty check` and `qlty smells` displayed as editor annotations
- **Quick fixes** — Apply suggested fixes from the lightbulb menu
- **Fix all in file** — Batch-fix all issues or all issues for a specific rule in one action
- **Fix all in project** — Fix every instance of a rule across the entire project
- **Format on save** — Automatically run `qlty fmt` when saving files
- **Status bar widget** — Shows current Qlty analysis state

## Prerequisites

Install the [Qlty CLI](https://docs.qlty.sh/getting-started/installation), then initialize it in your project:

```sh
qlty init
```

## Installation

Download the latest release `.zip` from [Releases](../../releases), then in your IDE:

**Settings > Plugins > gear icon > Install Plugin from Disk...** and select the `.zip` file.

## Usage

Once installed, Qlty automatically analyzes files when you open or save them. Issues appear as inline annotations in the editor and in the Problems panel.

Additional actions are available under **Tools > Qlty**:

- **Analyze Current File** — Run Qlty on the active file
- **Analyze Project** — Run Qlty on the entire project
- **Format Current File** — Run `qlty fmt` on the active file

Settings are available under **Settings > Tools > Qlty** to enable/disable analysis and format on save.

## Contributing & Development

**Requirements:** JDK 21+

To develop the plugin:

```sh
./gradlew build
```

Launch a sandbox IDE with the plugin loaded:

```sh
./gradlew runIde
```

### Packaging

```sh
./gradlew buildPlugin
```

The plugin `.zip` will be in `build/distributions/`. Install it via **Settings > Plugins > Install Plugin from Disk...**.

## License

This plugin is licensed under the [Business Source License 1.1](LICENSE.md).
