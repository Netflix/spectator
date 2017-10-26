# Contributing to Spectator

First off, thanks for taking the time to contribute!

The following is a set of guidelines for contributing to Spectator. Use your best judgment, and
feel free to propose changes to this document in a pull request.

**Table of Contents**

[How can I contribute?](#how-can-i-contribute)
* [Asking Questions](#asking-questions)
* [Reporting Issues or Feature Requests](#reporting-issues-or-feature-requests)
* [Contributing Code](#contributing-code)

[Guides](#guides)
* [Issue Labels](#issue-labels)
* [Git Commit Messages](#git-commit-messages)
* [Build and Test](#build-and-test)
* [License Headers](#license-headers)
* [Versions and Compatibility](#versions-and-compatibility)
* [Updating Documentation](#updating-documentation)

## How Can I Contribute?

### Asking Questions

If you have a question, then you can ask on the [mailing list] or by filing an [issue]. There
is not a strong preference among the developers, however, users that are not actively involved
in development may be more likely to see the question on the mailing list.

[mailing list]: https://groups.google.com/forum/#!forum/netflix-atlas
[issue]: https://github.com/Netflix/spectator/issues

### Reporting Issues or Feature Requests

Issues and feature requests are managed via [GitHub issues][issue]. When filing an issue for
a bug, we would appreciate if you first check open issues to see if there are any similar
requests.

When **reporting a bug**, then please include the following:

* Expected results
* Actual results
* Exact steps to reproduce the problem, bonus points for providing a failing unit test

When **requesting a feature**, then please try to answer the following:

* What does this allow a user to accomplish that they cannot do now?
* How urgent is the need?
* Does it align with the goals of Spectator?

### Contributing Code

[APLv2]: https://github.com/Netflix/spectator/blob/master/LICENSE

By contributing code, you agree to license your contribution under the terms of the [APLv2].
To submit code:

* Create a fork of the project (this includes Netflix contributors, do not push branches
  directly to the main repository)
* Create a branch for your change
* Make changes and add tests
* Commit the changes following the [commit guidelines](#git-commit-messages)
* Push the branch with your changes to your fork
* Open a pull request against the Spectator project

#### Testing

Where possible, test cases should be added to cover the new functionality or bug being
fixed. Test cases should be small, focused, and quick to execute.

#### Pull Requests

The following guidelines are to help ensure that pull requests (PRs) are easy to review and
comprehend.

* **One PR addresses one problem**, conflating issues in the same PR makes it more difficult
  to review and merge.
* **One commit per PR**, the final merge should have a single commit with a
  [good commit message](#git-commit-messages). Note, we can squash and merge via GitHub
  so it is fine to have many commits while working through the change and have us squash
  when it is complete. The exception is dependency updates where the
  only change is a dependency version. We typically do these as a batch with separate commits
  per version and merge without squashing. For this case, separate commits can be useful to
  allow for a git bisect to pinpoint a problem starting with a dependency change.
* **Reference related or fixed issues**, this helps us get more context for the change.
* **Partial work is welcome**, submit with a title including `[WIP]` (work in progress) to
  indicate it is not yet ready.
* **Keep us updated**, we will try our best to review and merge incoming PRs. We may close
  PRs after 30 days of inactivity. This covers cases like: failing tests, unresolved conflicts
  against master branch or unaddressed review comments.

## Guides

### Issue Labels

For [issues][issue] we use the following labels to quickly categorize issues:

| Label Name     | Description                                                               |
|----------------|---------------------------------------------------------------------------|
| `bug`          | Confirmed bugs or reports that are very likely to be bugs.                |
| `enhancement`  | Feature requests.                                                         |
| `discussion`   | Requests for comment to figure out the direction.                         |
| `help wanted`  | Help from the community would be appreciated. Good first issues.          |
| `question`     | Questions more than bug reports or feature requests (e.g. how do I do X). |

### Git Commit Messages

Commit messages should try to follow these guidelines:

* First line is no more than 50 characters and describes the changeset.
* The body of the commit message should include a more detailed explanation of the change.
  It is ok to use markdown formatting in the explanation.

More information can be found in the [Git docs]. Sample message:

```
Short (50 chars or less) summary of changes

More detailed explanatory text, if necessary.  Wrap it to
about 72 characters or so.  In some contexts, the first
line is treated as the subject of an email and the rest of
the text as the body.  The blank line separating the
summary from the body is critical (unless you omit the body
entirely); tools like rebase can get confused if you run
the two together.

Further paragraphs come after blank lines.

  - Bullet points are okay, too

  - Typically a hyphen or asterisk is used for the bullet,
    preceded by a single space, with blank lines in
    between, but conventions vary here
```

[Git docs]: https://git-scm.com/book/en/v2/Distributed-Git-Contributing-to-a-Project

### Build and Test

The Spectator build uses Gradle. If you do not already have it installed, then you can use the
included launcher script. To do a basic build and run tests:

```
$ ./gradlew build
```

For making changes, you are welcome to use whatever editor you are comfortable with. Most
current developers on the project use Intellij IDEA.

### License Headers

Spectator is licensed under the terms of the [APLv2]. License headers must be included on source
files and that is checked as part of the PR validation. To check license headers locally:

```
$ ./gradlew license
```

### Versions and Compatibility

The Spectator libraries follow a semantic versioning scheme. Backwards incompatible changes
should be marked with an incremented major version number. Forwards compatibility may work,
but is in not required or guaranteed. It is highly recommended that all `spectator-*` versions
in the classpath are the same.

Prior to 1.0, it was mostly backwards compatible with major changes resulting in the minor
version being incremented.

### Updating Documentation

The documentation for this project is created using [mkdocs] with the [material theme]. To
make changes simply edit the markdown files. To see rendered output run:

```
$ mkdocs serve
```

After the changes are done, submit to the project as a pull request. After a release is made
the documentation can be updated by running:

```
$ scripts/update-gh-pages.sh ${VERSION_TAG}
```

[mkdocs]: http://www.mkdocs.org/
[material theme]: http://squidfunk.github.io/mkdocs-material/
