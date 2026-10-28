# Unpublished Android PR draft

**Local draft. Publication requires the maintainer's explicit authorization.**

Source: `Umberto-DEV/melonDS-android:main` →
`rafaelvcaetano/melonDS-android:master`.
Functional candidate: `89053f0fd96ef2bc616849deb15900e1d6b09f43`;
core: `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`.
Refresh source/target revisions and insert CI and companion-PR links before publication.

Title: Add Wi-Fi settings and safer BIOS and cheat workflows, fix Android stability

This adds firmware Wi-Fi configuration, settings backup and searchable cheat
selectors, and fixes Android paths that could lose imported data, retain blocked
work or use an incompatible renderer after GPU initialization failed. Existing
save formats and default optional settings remain unchanged.

## User-facing changes

- Configure three WFC slots and primary/secondary DNS in Settings; toggle slots
  from the pause menu. DS/DSi firmware checksums and connection blocks are handled
  by the existing configuration flow. This does not promise server availability.
- Export/restore settings and layouts. Guided BIOS setup recognizes candidates,
  reports DS/DSi results separately and changes preferences only after actual
  validation. Copies use unique staging documents and publish after both streams
  close; failed cleanup names the remaining temporary file and permits a full retry.
- Search cheat folders, codes and generated selector options; highlight matches,
  preserve the query and navigate without enabling the result. Recognized modifier
  families expose searchable species/level/nature/starter choices and exclusions.
- Keep selector colors within the existing theme, provide 48dp targets and one
  radio action, and retain a usable editor with large text and the real keyboard.
  Keyboard Done dismisses editing without enabling a cheat. System icons follow
  the dialog surface in both themes.
- Add hold-to-fast-forward, combination hotkeys, optional fast-forward muting and
  optional forward-only clock synchronization. Preserve the ordinary toggle and
  keep clock synchronization disabled by default.

## Data integrity and lifecycle fixes

| Trigger | Previous behavior | Result |
| --- | --- | --- |
| Malformed/cancelled XML or DAT import | Existing database could be deleted, or blocking I/O could retain work | One Room replacement transaction, bounded parser buffering, owned input closure, provider-open cancellation and a subsequent import that succeeds. |
| Slow/cancelled BIOS setup followed by another setup | A partial canonical file could be adopted as valid | Serialized setup, noncanonical staging, validation before preferences, explicit source/read/rename/cleanup errors. |
| Microphone source change or concurrent teardown | Out-of-range PCM, stale buffer cursor or stream/state races | Saturating PCM, per-session cursor and serialized control ownership, without adding a blocking mutex to the realtime callback. |
| ROM scan error or overlapping refresh | Spinner could remain active; old list values could change | Serialized scans, finally reset, stable snapshots and cache preservation when provider lookup fails. Sorting persists the new value. |
| Accelerated renderer factory fails | Core installs Software while frontend performs an accelerated downcast | Frontend tracks Software, continues frames/screenshots and preserves the requested renderer preference. |
| Invalid NAND/BIOS/TMD or fragmented metadata read | Invalid input could appear successful or leave incorrect cleanup | Length/identity checks, complete 520-byte TMD read or EOF, correct cleanup arguments. |
| Display reconnect/rotation or pointer changes | Missing screen/layout or stale touch coordinates | Verified layout transitions and MotionEvent handling, preserving intentionally hidden screens. |

Also retains safer save-write error/retry handling, output-audio open/resume
checks, input release on cancellation/focus loss, session coroutine ownership,
lid-pause deadlines across stop, FPS constraint cleanup, independent ROM icons,
bounded/serialized 7z decoding, correct XML buffer offsets, LCD/scanline alignment
and bounded JNI local references while listing DSiWare titles.

## CI and resources

Request `platform-tools`, use supported Node24 workflow actions, verify forks/PRs
with read-only permissions and no release secrets, retain upstream-only Nightly
publication, and upload the actual verification APK. Remove the invalid NDK
output reference and apply the manual Play Store actor restriction at job level.
Complete missing locale strings, time/plural formatting and Scanlines labels.

## Provenance and issue scope

Search is adapted from [#1677](https://github.com/rafaelvcaetano/melonDS-android/pull/1677)
by [takanuva15](https://github.com/takanuva15), extended to generated options and
precise navigation. Preserve original contributor credits for WailAbou
[#1606](https://github.com/rafaelvcaetano/melonDS-android/pull/1606)/
[#1608](https://github.com/rafaelvcaetano/melonDS-android/pull/1608), dysprosium
[#1625](https://github.com/rafaelvcaetano/melonDS-android/pull/1625), N3MI-DG
[#1648](https://github.com/rafaelvcaetano/melonDS-android/pull/1648), DLCSharp
[#1589](https://github.com/rafaelvcaetano/melonDS-android/pull/1589), and corigne
for selected parts of [#1666](https://github.com/rafaelvcaetano/melonDS-android/pull/1666).
The full process-death recovery rewrite is not claimed integrated.

Fixes #1678: the reported JNI local-reference lifetime defect.

Implements settings/layout backup requested in #264. Resource coverage addresses
#1253; linguistic quality is not certified by lint. DAT support requested in
#1631 already existed upstream; this proposal hardens parsing and import integrity.
Audio #1644/#1635, RTC #1652/#1604, 7z/library #1519/#1378, microphone #1679/#1285,
DSi #1651/#1099 and external-display #1663/#1641/#1619 remain partial outside
the demonstrated contracts. Optional mute is a workaround for #1602.

Do not add blanket closing keywords for those reports or claim that older closed
issues #1367/#961/#1626 were closed by this work. Adapted third-party PRs remain
separate upstream submissions. See the [integration and before/after report](../2026-10-28-refinement/INTEGRATION-REPORT.md)
and [per-item coverage](../2026-10-28-refinement/ISSUE-PR-COVERAGE.csv).

## Validation

- 267 JVM/Robolectric tests, zero failures/errors/skips; includes BIOS integration,
  search/navigation, scan, import and input regressions.
- ARM64 debug app and test APK build; lint 0 errors, 474 warnings.
- 52 ART/JNI passes plus 3 opt-in skips per environment: Android 13 ARM64/4KiB,
  Android 15 ARM64/4KiB and Android 15 ARM64/16KiB.
- Separate real WorkManager test passes on all three: duplicate KEEP suppressed,
  cancellation reaches the provider, foreground notification removed, database
  unchanged and the next KEEP request succeeds.
- Six native Android targets pass on all three; host TSan/ASan/UBSan checks are
  reported separately. Renderer factory-boundary regressions have seven failing
  baseline scenarios and ten passing corrected scenarios.
- Actual Android GLES3.1 compiler failure is exercised through the complete app
  with original generated homebrew. Software control, Compute and OpenGL requests
  each show advancing frames, presentation and unchanged preferences after fallback.
- Native visual inspection covers light/dark, IT/EN, large text and actual IME/Done.
  Independent review of the frozen diff and final deltas has no open findings.

The functional candidate APK SHA256 is
`72c3e4363f74da95e9e02df44fa3cba523ddf51ca2216e6992d220682926ec1b`.
[Commands, build identity and boundaries](../2026-10-28-refinement/VALIDATION.md).
Insert successful CI links for the actual submitted source; earlier CI does not
verify a newer commit. This candidate has not been installed on the physical Thor.

The previous app/core pair had a narrow real-game raw-save/SAF/cold-restart check;
it is historical integration evidence. No universal game compatibility, faster
normal startup, reduced power/RAM/GPU cost or improved 6x quality is claimed.
Physical microphone/controllers/displays, manual TalkBack and full Android
Activity rotation with a slow external provider remain separate acceptance gates.

## Paired integration

Companion core proposal: insert URL only after publication authorization.
Source is `Umberto-DEV/melonDS-android-lib:main`. Merge core first, then point the
app gitlink/submodule URL to the accepted revision and repeat relevant checks if
that changes it. WFC firmware editing does not require the optional DNS override API.

Supersedes the closed, unmerged Android proposals #1682/#1681. Their descriptions
and contributor history are preserved. Publication is still gated; preparing
this draft does not open or close any GitHub item.
