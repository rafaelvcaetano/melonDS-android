# Official Nightly comparison and PR preparation plan

**Goal:** compare the current fork with the latest published official Nightly and
prepare two short, evidence-backed PR drafts for maintainer review.

**Architecture:** pin the published APK, its annotated tag, app commit and core
gitlink. Compare immutable Git trees and histories, then separate user features,
fixes, inherited behavior, tests, documentation and fork-specific packaging.

**Tools:** Git, read-only GitHub API, APK manifest inspection and small Python
inventory scripts. No emulator or physical device is needed for this comparison.

**Specification:** the maintainer's 29 October request: use the latest official
Nightly, keep the previous PR structure, write short English sections with
bullets, link detailed evidence, and leave push/publication for later approval.

## Constraints

- Work on existing `main`; preserve unrelated local changes and contributor credit.
- No branch, worktree, history rewrite, push, PR publication or device installation.
- Do not treat historical Thor testing as acceptance of the newer candidate.
- Do not infer performance, energy use or broad compatibility from source counts.
- Keep one canonical official APK as a distinct future comparison target.

## Review focus

- A rolling release's original publication date is not its latest asset date.
- An annotated tag must be resolved to its commit; the core comes from that tree.
- Existing upstream features and duplicated historical patches must not become
  new-feature or fixed-issue counts.
- Debug/profiling/release APKs are not comparable performance builds.
- A PR from `main` includes its full file diff, including fork configuration and
  accumulated documentation; the short description does not filter that diff.

## Tasks and deliverables

- [x] Identify Nightly release, asset hash, tag, source commit, workflow and core;
  verify the downloaded APK's hash and declared version.
- [x] Generate complete app/core file and commit inventories, identify repeated
  patches, and review differences against the previous reports.
- [x] Write `docs/maintenance/2026-10-29-nightly-comparison/README.md`, supporting
  inventories and a bounded Thor comparison protocol.
- [x] Rewrite `NEXT-APP-PR.md` and `NEXT-CORE-PR.md` as short English bullet lists
  with explicit credits, issue scope and links to detailed evidence.
- [x] Verify pinned revisions, inventory completeness, source/artifact identity,
  links, wording and `git diff --check`; save a documentation-only local commit.

This is an audit/documentation task. Existing functional results are cited with
their exact candidate; no new runtime or benchmark result is created by it.
