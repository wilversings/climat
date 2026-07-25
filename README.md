

<div align="center">

![LastCommitOnMaster](https://img.shields.io/github/last-commit/climat-project/climat/master?label=last%20commit%20on%20master&style=for-the-badge)
![Build](https://img.shields.io/github/actions/workflow/status/climat-project/climat/build.yml?branch=master&style=for-the-badge)
[![Version](https://img.shields.io/npm/v/climat?style=for-the-badge)](https://www.npmjs.com/package/climat)
![NodeVersion](https://img.shields.io/node/v/climat?style=for-the-badge)
[![GitHub license](https://img.shields.io/npm/l/climat?style=for-the-badge)](https://github.com/climat-project/climat/blob/master/LICENSE.md)

<br/>

<img alt="Climat logo" src="https://raw.githubusercontent.com/climat-project/climat/master/docs/static/img/logo-name-prerelease-tag.svg" width="500px"/>
<br/><br/>

Powerful CLI Macros
<br />
<a href="https://climat-project.github.io/"><strong>Explore the docs »</strong></a>
<br />
<br />
[Report Bug](https://github.com/climat-project/climat/issues/new?labels=&projects=&template=bug_report.md&title=[BUG]) 
·
[Request Feature](https://github.com/climat-project/climat/issues/new?labels=&projects=&template=feature_request.md&title=[FEATURE])
·
[Ask Question](https://github.com/climat-project/climat/issues/new?labels=&projects=&template=question.md&title=[QUESTION])
·
[Contribute](https://github.com/climat-project/climat/blob/master/CONTRIBUTING.md)
</div>

# ⚠️ PRE-RELEASE: Everything is subject to change ⚠️

Generate powerful macros for your CLI tools with a simple and smart approach.

- ✍️ Write complex CLI interfaces in a declarative style.
- 🗄️ Combine many inconsistent CLI tools under a single consistent one.
- 🥷 Or just rewrite the CLI interface of a tool for a faster and more user-tailored experience.

## Installation

```sh
npm i -g climat
```

## Usage

Write your macro in `sgit.cli`

```cli
sgit {
    sub acp(amend a: flag) {
        action { 
            git add . && 
            git commit @{amend ? "--amend"} && 
            git push @{amend ? "--force"} 
        }
    }
    sub cf(branch: arg, force f: flag) {
        action { 
            git checkout feature/@{branch} @{force ? "--force"} 
        }
    }
}
```

Installing this via `sudo climat install sgit.cli` will generate

```shell
sgit acp             # Will add all files to index, commit and push
sgit acp --ammend    # Will add all files to index, ammend last commit and push force
sgit acp -a          # Can use shorthands for parameters
sgit cf myFeature    # git checkout feature/myFeature
```

## What does it stand for?

**CLi** **MA**cro **T**ree
