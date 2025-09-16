# light-semver — A tiny, strict SemVer 2.0.0 library for Java 🚀🎉

**light-semver** is a small, immutable, and strict Semantic Version (SemVer 2.0.0) implementation written in Java.
It focuses on correctness, clear error messages, and a tiny surface area — perfect when you want a predictable SemVer object without pulling a big dependency.

---

# Introduction

Welcome! 👋 `light-semver` provides a single immutable `Version` class that implements SemVer 2.0.0 parsing, validation, canonicalization, precedence comparison, and a couple of helpful utilities. It's intentionally small and strict: it rejects invalid versions rather than silently normalizing them.

Key implementation notes:

* Numeric core parts (`major`, `minor`, `patch`) are stored as **`BigInteger`** so arbitrarily large numeric parts are supported.&#x20;
* Pre-release and build metadata are stored as lists of identifiers. Build metadata is ignored for precedence but **included** in equality/hashCode.&#x20;
* A private `Parser` performs validation with clear errors — no complex global regexes.&#x20;

---

# Features ✅

* Full SemVer 2.0.0-compatible parsing & validation.&#x20;
* Immutable `Version` object with getters for `major`, `minor`, `patch`, `prerelease`, `build`.&#x20;
* `Version.parse(String)` throws `IllegalArgumentException` for invalid input; `Version.tryParse(String)` returns `Optional.empty()` on invalid input.&#x20;
* `compareTo(Version)` implements SemVer precedence (ignores build metadata).&#x20;
* `PRECEDENCE` comparator alias for `compareTo`, and `PRECEDENCE_WITH_BUILD` to deterministically break ties using build meta.&#x20;
* Bump helpers: `bumpMajor()`, `bumpMinor()`, `bumpPatch()` that reset lower fields and drop prerelease/build.&#x20;
* Canonical `toString()` is cached for fast repeated use.&#x20;

---

# Quick Start 🚦

```java
import dev.meinicke.semver.Version;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;

public class Example {
    public static void main(String[] args) {
        // parse (throws IllegalArgumentException on invalid)
        Version v1 = Version.parse("1.2.3-alpha.1+build.001");
        System.out.println(v1); // -> "1.2.3-alpha.1+build.001"

        // tryParse (safe)
        Optional<Version> maybe = Version.tryParse("not-a-version");
        System.out.println(maybe.isPresent()); // -> false

        // programmatic construction
        Version v2 = Version.of(new BigInteger("2"), new BigInteger("0"), new BigInteger("0"),
                                Arrays.asList("rc", "1"),
                                Arrays.asList("exp", "sha", "5114f85"));
        System.out.println(v2); // -> "2.0.0-rc.1+exp.sha.5114f85"

        // bump helpers
        Version bumpedMajor = v1.bumpMajor(); // 2.0.0
        System.out.println(bumpedMajor);

        // compare and sort
        int cmp = v1.compareTo(v2); // precedence comparison (build ignored)
    }
}
```

---

# API Reference (methods & behavior) 📚

**Constructors / Factories**

* `public static Version parse(String input)`
  Parse input strictly. Throws `IllegalArgumentException` with a clear message if invalid.&#x20;

* `public static Optional<Version> tryParse(String input)`
  Returns `Optional.empty()` if input is invalid or `null`.

* `public static Version of(BigInteger major, BigInteger minor, BigInteger patch, List<String> prereleaseList, List<String> buildList)`
  Programmatic constructor. Validates all identifiers and that numeric components are non-negative.&#x20;

**Accessors**

* `BigInteger getMajor()`, `getMinor()`, `getPatch()` — never null.
* `List<String> getPrerelease()`, `getBuild()` — unmodifiable lists.
* `boolean isPrerelease()` — true if there are prerelease identifiers.

**Comparison & hashing**

* `public int compareTo(Version other)` — SemVer precedence (major/minor/patch then prerelease per SemVer rules). Build metadata **ignored** for precedence.&#x20;
* `public static final Comparator<Version> PRECEDENCE` — alias to `compareTo`.
* `public static final Comparator<Version> PRECEDENCE_WITH_BUILD` — precedence comparator, with build metadata used as deterministic tiebreaker (numeric vs lexicographic rules applied).&#x20;
* `equals()` and `hashCode()` include build metadata — that means `1.0.0+build.1` ≠ `1.0.0+build.2`.&#x20;

**Bumps**

* `bumpMajor()` — increments major, sets minor & patch to 0, clears prerelease/build.
* `bumpMinor()` — increments minor, sets patch to 0, clears prerelease/build.
* `bumpPatch()` — increments patch, clears prerelease/build.

---

# Examples — real usage scenarios 🧪

## 1) Sorting releases by precedence (ignoring builds)

```java
List<Version> versions = Arrays.asList(
    Version.parse("1.0.0-alpha"),
    Version.parse("1.0.0-alpha.1"),
    Version.parse("1.0.0"),
    Version.parse("1.0.0-beta")
);
versions.sort(Version.PRECEDENCE);
```

SemVer precedence yields the correct order (see spec sequence example). The tests include the canonical SemVer precedence example and verify sorting.&#x20;

## 2) Deterministic ordering that includes build as tiebreaker

If you need a deterministic total order (e.g., for stable storage ordering), use:

```java
versions.sort(Version.PRECEDENCE_WITH_BUILD);
```

This comparator first uses SemVer precedence; if equal, it uses build metadata identifiers as a tiebreaker (numeric vs lexical when both numeric).&#x20;

## 3) CI release bump examples

Typical automation:

* For a breaking change: `current.bumpMajor()` -> tag and publish `X+1.0.0`.
* For backward-compatible feature: `current.bumpMinor()`.
* For bugfix: `current.bumpPatch()`.

Because bump helpers clear prerelease/build metadata, the result is a clean stable release string ready for tagging.&#x20;

## 4) Validating incoming version strings

Use `Version.tryParse` to validate user input or metadata. Use `parse` when you want deterministic failure with a clear message that explains what's wrong.

---

# Validation rules & edge cases (what's rejected/accepted) ⚖️

**Rejected / invalid cases**

* Core version must have **three** dot-separated numeric identifiers (`major.minor.patch`). e.g. `"1.0"` or `"1"` or `""` are invalid.&#x20;
* Core numeric components must be numeric only and **non-negative**.&#x20;
* **No leading zeros** on core numeric components (so `"01.0.0"` is invalid; `"0.0.0"` is valid).&#x20;
* Pre-release numeric identifiers must not have leading zeros (e.g., `"1.0.0-01"` is invalid).&#x20;
* Empty prerelease or build sections (like `1.0.0-` or `1.0.0+`) are invalid.&#x20;
* Identifiers may only contain ASCII alphanumerics and hyphen. Characters like `@`, `!` etc. are invalid.&#x20;

**Accepted / permitted**

* Build metadata identifiers **can** contain leading zeros in numeric identifiers (spec allows that). Example: `1.0.0+001` is valid and kept as-is.&#x20;
* Very large numeric components: because core parts are `BigInteger`, values like `123456789012345678901234567890.0.0` parse successfully. There's an explicit test covering that.&#x20;

---

# Comparators, equality & nuances 🔍

* `compareTo` and `PRECEDENCE` implement the SemVer precedence rules; **build metadata is ignored** for precedence. That means two versions that differ only in build identifiers compare equal for precedence (useful for semver ordering in release graphs).&#x20;
* However, `equals()` **includes** build metadata (and `hashCode()` too). So two versions that differ only by build metadata are **not** `equal`. This is a deliberate design choice (often desired when build metadata encodes identity like a CI run).&#x20;
* `PRECEDENCE_WITH_BUILD` gives a deterministic order even when precedence is equal by using build identifiers as the tiebreaker — compare numerically when both identifiers are numeric, otherwise lexicographically. This offers a stable sorted order for storage/display.&#x20;

---

# Thread-safety & Immutability 🛡️

* `Version` instances are **immutable**: fields are final, lists exposed are unmodifiable, and the class caches a canonical `toString()` value. This makes them thread-safe and safe to share across threads.&#x20;

---

# Testing 🧩

The repository includes a comprehensive `VersionTest` suite exercising:

* parsing, canonicalization, and `toString()`,
* precedence examples from the SemVer spec,
* build-vs-precedence semantics,
* bump helpers,
* invalid input cases (leading zeros, invalid chars, empty parts),
* very large numeric handling (BigInteger).&#x20;

If you add behavior, please add unit tests for:

* corner cases in parsing,
* comparator properties (antisymmetry, transitivity),
* equals/hashCode contracts,
* integration with your own release pipeline.

---

# Integration ideas & CI snippets 🔧

## Using in Gradle/Maven (example coordinates — hypothetical)

> If you publish to a Maven repository, you might use coordinates like:

* `groupId`: `dev.meinicke`
* `artifactId`: `light-semver`
* `version`: `0.1.0`

(Adjust to your actual publishing naming.)

## Example GitHub Actions step — bump patch on tag

Here's a pseudo-workflow idea (bash-like):

```bash
# read current version (assume you store it somewhere or deduce from last git tag)
CURRENT="1.2.3"
# use the library's bumpPatch() semantics in your Java/Gradle task or simply increment with a script
NEW="1.2.4"
git tag -a "v$NEW" -m "Release $NEW"
git push origin "v$NEW"
```

Alternatively, write a tiny Java utility using `Version.parse(current).bumpPatch().toString()` as the canonical new tag.

---

# FAQ & Troubleshooting ❓

**Q:** Why does `Version.parse("01.0.0")` fail?
**A:** Leading zeros in core numeric identifiers are forbidden by SemVer and this library enforces that strictly. Use `1.0.0`.&#x20;

**Q:** Should build metadata be ignored?
**A:** SemVer says build metadata is ignored for precedence — this library follows that. Equality *does* include build metadata, intentionally, so you can represent CI-specific identities.&#x20;

**Q:** Are prerelease numeric identifiers with leading zeros allowed?
**A:** No — numeric prerelease identifiers must not contain leading zeros. Example: `alpha.01` is invalid.&#x20;

**Q:** Large version numbers okay?
**A:** Yes — `BigInteger` supports large numeric components. There's a test validating this.&#x20;

---

# Contribution & License ✨

This README is based on the two source files you provided (implementation + tests). If you want:

* to add more helper utilities (parsing helpers, range queries),
* publish to Maven Central,
* add Kotlin/Scala wrappers,
* or add a `VersionRange` parser (e.g., `^1.2.0`, `~2.0`),

I can help draft those changes and tests. (I can't publish for you, but I can provide the code & CI configs.)

---

# Final notes — TL;DR 😄

* `light-semver` is: strict ✅ immutable ✅ SemVer 2.0.0 compatible ✅ simple ✅
* Use `Version.parse()` for strict parsing, `tryParse()` when you need safe validation.&#x20;
* `compareTo` follows SemVer precedence and ignores build metadata; equality includes build metadata.&#x20;
* Tests are provided and comprehensive. Run them and add more for any new behavior.&#x20;

---

If you'd like, I can:

* generate a `pom.xml` and `build.gradle` with suggested coordinates,
* produce a `CHANGELOG.md` template and GitHub Actions workflow to tag and publish releases, or
* extend the library with `VersionRange` matching and comparator utilities.

Tell me which direction you want—let's make this library shine ✨🧾🚀