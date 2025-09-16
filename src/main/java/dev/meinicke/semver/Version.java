package dev.meinicke.semver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;

/**
 * Immutable, SemVer 2.0.0 compatible version object.
 *
 * <p>This class provides strict parsing and validation following the SemVer 2.0.0
 * specification without using regular expressions. It strives for clean structure,
 * clear error messages and minimal top-level static helpers. Parsing implementation
 * is encapsulated in a private {@code Parser} helper to reduce static utility methods
 * exposed at class scope.
 *
 * <p>Notes:
 * <ul>
 *   <li>Core numeric components (major/minor/patch) are stored as {@link BigInteger} and must be non-negative.</li>
 *   <li>No leading zeros are allowed for core numeric components ("0" is valid; "01" is invalid).</li>
 *   <li>Prerelease identifiers must consist only of ASCII alphanumerics and hyphen.
 *       Numeric prerelease identifiers must not contain leading zeros.</li>
 *   <li>Build metadata identifiers must consist only of ASCII alphanumerics and hyphen.
 *       Leading zeros are allowed for build numeric identifiers (per spec).
 *   <li>{@link #compareTo(Version)} implements SemVer precedence rules; build metadata is <em>ignored</em> for precedence.
 *   <li>Equality and {@link #hashCode()} include build metadata (so versions differing only by build are not equal).</li>
 * </ul>
 *
 * Instances are immutable and thread-safe.
 */
public final class Version implements Comparable<Version> {

    private final @NotNull BigInteger major;
    private final @NotNull BigInteger minor;
    private final @NotNull BigInteger patch;
    private final @NotNull List<String> prerelease;    // unmodifiable list
    private final @NotNull List<String> build;         // unmodifiable list
    private final @NotNull String canonical;           // cached canonical string representation

    /**
     * Comparator that compares versions according to SemVer precedence rules (same as {@link #compareTo}).
     * Build metadata is ignored.
     */
    public static final @NotNull Comparator<Version> PRECEDENCE = Version::compareTo;

    /**
     * Comparator that compares by SemVer precedence but, if equal by precedence, uses build metadata as a deterministic
     * tiebreaker. Build metadata comparison is lexicographic per identifier; when both identifiers are numeric they are
     * compared numerically.
     *
     * <p>Important: this comparator is a deterministic ordering aid only — it does not change SemVer semantics.
     */
    public static final @NotNull Comparator<Version> PRECEDENCE_WITH_BUILD = (a, b) -> {
        int p = a.compareTo(b);
        if (p != 0) return p;
        List<String> ab = a.build;
        List<String> bb = b.build;
        int min = Math.min(ab.size(), bb.size());
        for (int i = 0; i < min; i++) {
            String as = ab.get(i);
            String bs = bb.get(i);
            boolean an = Parser.isNumeric(as);
            boolean bn = Parser.isNumeric(bs);
            if (an && bn) {
                BigInteger A = new BigInteger(as);
                BigInteger B = new BigInteger(bs);
                int cmp = A.compareTo(B);
                if (cmp != 0) return cmp;
            } else {
                int cmp = as.compareTo(bs);
                if (cmp != 0) return cmp;
            }
        }
        return Integer.compare(ab.size(), bb.size());
    };

    // Private constructor. Use factory methods.
    private Version(@NotNull BigInteger major, @NotNull BigInteger minor, @NotNull BigInteger patch, @NotNull List<String> prerelease, @NotNull List<String> build) {
        this.major = Objects.requireNonNull(major, "major");
        this.minor = Objects.requireNonNull(minor, "minor");
        this.patch = Objects.requireNonNull(patch, "patch");
        this.prerelease = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(prerelease, "prerelease")));
        this.build = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(build, "build")));
        this.canonical = buildCanonical();
    }

    // -------------------- Factories --------------------

    /**
     * Parse a SemVer string and return a {@link Version} instance.
     *
     * @param input non-null semver string
     * @return parsed Version
     * @throws IllegalArgumentException when input is invalid
     */
    public static @NotNull Version parse(@NotNull String input) {
        return new Parser(input).parse();
    }

    /**
     * Try to parse. Returns empty {@link Optional} on failure.
     */
    public static @NotNull Optional<Version> tryParse(@Nullable String input) {
        if (input == null) return Optional.empty();
        try {
            return Optional.of(parse(input));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Construct programmatically from components. Lists may be null or empty.
     */
    public static @NotNull Version of(@NotNull BigInteger major,
                                      @NotNull BigInteger minor,
                                      @NotNull BigInteger patch,
                                      @Nullable List<String> prereleaseList,
                                      @Nullable List<String> buildList) {
        Objects.requireNonNull(major, "major");
        Objects.requireNonNull(minor, "minor");
        Objects.requireNonNull(patch, "patch");
        if (major.signum() < 0 || minor.signum() < 0 || patch.signum() < 0) {
            throw new IllegalArgumentException("major/minor/patch must be non-negative");
        }
        List<String> pr = prereleaseList == null ? Collections.emptyList() : new ArrayList<>(prereleaseList);
        List<String> bd = buildList == null ? Collections.emptyList() : new ArrayList<>(buildList);

        // validate identifiers
        for (String id : pr) Parser.validatePrereleaseIdentifier(id);
        for (String id : bd) Parser.validateBuildIdentifier(id);

        return new Version(major, minor, patch, pr, bd);
    }

    // -------------------- Accessors --------------------

    public @NotNull BigInteger getMajor() { return major; }

    public @NotNull BigInteger getMinor() { return minor; }

    public @NotNull BigInteger getPatch() { return patch; }

    public @NotNull List<String> getPrerelease() { return prerelease; }

    public @NotNull List<String> getBuild() { return build; }

    public boolean isPrerelease() { return !prerelease.isEmpty(); }

    // -------------------- Bump helpers --------------------

    public @NotNull Version bumpMajor() {
        return new Version(major.add(BigInteger.ONE), BigInteger.ZERO, BigInteger.ZERO, Collections.emptyList(), Collections.emptyList());
    }

    public @NotNull Version bumpMinor() {
        return new Version(major, minor.add(BigInteger.ONE), BigInteger.ZERO, Collections.emptyList(), Collections.emptyList());
    }

    public @NotNull Version bumpPatch() {
        return new Version(major, minor, patch.add(BigInteger.ONE), Collections.emptyList(), Collections.emptyList());
    }

    // -------------------- Stringification --------------------

    @Override
    public @NotNull String toString() { return canonical; }

    private @NotNull String buildCanonical() {
        StringBuilder sb = new StringBuilder();
        sb.append(major.toString()).append('.').append(minor.toString()).append('.').append(patch.toString());
        if (!prerelease.isEmpty()) {
            sb.append('-');
            joinDot(sb, prerelease);
        }
        if (!build.isEmpty()) {
            sb.append('+');
            joinDot(sb, build);
        }
        return sb.toString();
    }

    private static void joinDot(@NotNull StringBuilder sb, @NotNull List<String> list) {
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append('.');
            sb.append(list.get(i));
        }
    }

    // -------------------- Comparable (SemVer precedence) --------------------

    @Override
    public int compareTo(@NotNull Version other) {
        Objects.requireNonNull(other, "other");
        int cmp = major.compareTo(other.major);
        if (cmp != 0) return cmp;
        cmp = minor.compareTo(other.minor);
        if (cmp != 0) return cmp;
        cmp = patch.compareTo(other.patch);
        if (cmp != 0) return cmp;

        boolean aPre = !this.prerelease.isEmpty();
        boolean bPre = !other.prerelease.isEmpty();
        if (!aPre && !bPre) return 0;
        if (!aPre) return 1;
        if (!bPre) return -1;

        int min = Math.min(this.prerelease.size(), other.prerelease.size());
        for (int i = 0; i < min; i++) {
            String aId = this.prerelease.get(i);
            String bId = other.prerelease.get(i);
            boolean aNum = Parser.isNumeric(aId);
            boolean bNum = Parser.isNumeric(bId);
            if (aNum && bNum) {
                BigInteger A = new BigInteger(aId);
                BigInteger B = new BigInteger(bId);
                int n = A.compareTo(B);
                if (n != 0) return n;
            } else if (aNum) {
                return -1;
            } else if (bNum) {
                return 1;
            } else {
                int n = aId.compareTo(bId);
                if (n != 0) return n;
            }
        }
        return Integer.compare(this.prerelease.size(), other.prerelease.size());
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof Version)) return false;
        Version v = (Version) o;
        return major.equals(v.major)
                && minor.equals(v.minor)
                && patch.equals(v.patch)
                && prerelease.equals(v.prerelease)
                && build.equals(v.build);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, prerelease, build);
    }

    // -------------------- Parser helper (reduces static helpers at class scope) --------------------

    /**
     * Parser encapsulates the parsing logic and validations. Keeping it private reduces the number
     * of static helper methods on the outer class and centralizes error messages.
     */
    private static final class Parser {
        private final @NotNull String input;

        Parser(@NotNull String input) {
            this.input = Objects.requireNonNull(input, "input").trim();
            if (this.input.isEmpty()) throw new IllegalArgumentException("Empty version string");
        }

        Version parse() {
            String s = input;

            int plus = s.indexOf('+');
            String buildPart = plus >= 0 ? s.substring(plus + 1) : "";
            String beforePlus = plus >= 0 ? s.substring(0, plus) : s;

            int dash = beforePlus.indexOf('-');
            String core = dash >= 0 ? beforePlus.substring(0, dash) : beforePlus;
            String prereleasePart = dash >= 0 ? beforePlus.substring(dash + 1) : "";

            // If a '-' is present but there's nothing after it, that's invalid (e.g. "1.0.0-")
            if (dash >= 0 && prereleasePart.isEmpty()) {
                throw new IllegalArgumentException("Empty prerelease part after '-' in version: '" + input + "'");
            }
            // If a '+' is present but there's nothing after it, that's invalid (e.g. "1.0.0+")
            if (plus >= 0 && buildPart.isEmpty()) {
                throw new IllegalArgumentException("Empty build metadata part after '+' in version: '" + input + "'");
            }

            String[] corePieces = splitExact(core, '.');
            if (corePieces.length != 3) {
                throw new IllegalArgumentException("Core version must be three dot-separated numeric identifiers: 'major.minor.patch' (found: '" + core + "')");
            }

            BigInteger major = parseCoreNumber(corePieces[0], "major");
            BigInteger minor = parseCoreNumber(corePieces[1], "minor");
            BigInteger patch = parseCoreNumber(corePieces[2], "patch");

            List<String> pre = prereleasePart.isEmpty() ? Collections.emptyList() : splitAndValidateIdentifiers(prereleasePart, true);
            List<String> bd = buildPart.isEmpty() ? Collections.emptyList() : splitAndValidateIdentifiers(buildPart, false);

            return new Version(major, minor, patch, pre, bd);
        }

        private static @NotNull String[] splitExact(@NotNull String s, char delim) {
            // do not allow leading/trailing empty core pieces — caller will detect count
            if (s.isEmpty()) return new String[0];
            List<String> parts = new ArrayList<>();
            int start = 0;
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) == delim) {
                    parts.add(s.substring(start, i));
                    start = i + 1;
                }
            }
            parts.add(s.substring(start));
            return parts.toArray(new String[0]);
        }

        private static BigInteger parseCoreNumber(String piece, String name) {
            if (piece == null || piece.isEmpty()) {
                throw new IllegalArgumentException(name + " component is missing");
            }
            for (int i = 0; i < piece.length(); i++) {
                char c = piece.charAt(i);
                if (c < '0' || c > '9') {
                    throw new IllegalArgumentException(name + " component must be numeric: '" + piece + "'");
                }
            }
            if (piece.length() > 1 && piece.charAt(0) == '0') {
                throw new IllegalArgumentException(name + " component must not contain leading zeros: '" + piece + "'");
            }
            return new BigInteger(piece);
        }

        private static List<String> splitAndValidateIdentifiers(String s, boolean isPrerelease) {
            if (s.isEmpty()) return Collections.emptyList();
            String[] parts = splitExact(s, '.');
            List<String> out = new ArrayList<>(parts.length);
            for (String p : parts) {
                if (p.isEmpty()) throw new IllegalArgumentException("Empty identifier in " + (isPrerelease ? "prerelease" : "build") + " part");
                if (isPrerelease) validatePrereleaseIdentifier(p);
                else validateBuildIdentifier(p);
                out.add(p);
            }
            return Collections.unmodifiableList(out);
        }

        // ---------- Validation logic centralized here ----------

        private static void validatePrereleaseIdentifier(String id) {
            if (id == null || id.isEmpty()) throw new IllegalArgumentException("Empty prerelease identifier");
            if (!isAsciiIdentifier(id)) throw new IllegalArgumentException("Prerelease identifier contains invalid characters: '" + id + "'");
            if (isNumeric(id) && id.length() > 1 && id.charAt(0) == '0') {
                throw new IllegalArgumentException("Numeric prerelease identifier must not contain leading zeros: '" + id + "'");
            }
        }

        private static void validateBuildIdentifier(String id) {
            if (id == null || id.isEmpty()) throw new IllegalArgumentException("Empty build identifier");
            if (!isAsciiIdentifier(id)) throw new IllegalArgumentException("Build identifier contains invalid characters: '" + id + "'");
        }

        private static boolean isAsciiIdentifier(@NotNull String id) {
            for (int i = 0; i < id.length(); i++) {
                char c = id.charAt(i);
                if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '-')) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isNumeric(@Nullable String s) {
            if (s == null || s.isEmpty()) return false;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c < '0' || c > '9') return false;
            }
            return true;
        }
    }

}
