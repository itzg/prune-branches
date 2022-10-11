A simple CLI to prune local git branches that have been merged via merge-commit or squash-and-merge.

## Usage

Run `prune-branches --help` to see the full set of options; however, typically you can go into the repo directory to prune and simply run it with no options.

## Installation

> All the installation methods require Java 8 or newer to be installed.

### Scoop

```shell
scoop bucket add itzg https://github.com/itzg/scoop-bucket.git
scoop install prune-branches 
```

### Homebrew

```shell
brew tap itzg/tap
brew install prune-branches
```

### Manually

Download the zip or tgz from [the latest release](https://github.com/itzg/prune-branches/releases/latest).