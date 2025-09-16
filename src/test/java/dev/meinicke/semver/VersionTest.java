package dev.meinicke.semver;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for {@code Version} (SemVer 2.0.0 compatible implementation).
 *
 * <p>These tests exercise parsing, validation, precedence (compareTo), equality/hashCode semantics,
 * bump helpers and the provided comparators. They are written assuming the {@code Version}
 * class follows SemVer 2.0.0 semantics (core numeric parts, prerelease rules, build ignored for
 * precedence but included in identity).
 */
public class VersionTest {

    @Test
    @DisplayName("parse and toString for basic versions")
    public void testParseBasic() {
        @NotNull Version v = Version.parse("1.2.3");
        assertEquals(new BigInteger("1"), v.getMajor());
        assertEquals(new BigInteger("2"), v.getMinor());
        assertEquals(new BigInteger("3"), v.getPatch());
        assertFalse(v.isPrerelease());
        assertEquals("1.2.3", v.toString());

        @NotNull Version zero = Version.parse("0.0.0");
        assertEquals("0.0.0", zero.toString());
    }

    @Test
    @DisplayName("parse with prerelease and build metadata")
    public void testParsePrereleaseAndBuild() {
        @NotNull Version v = Version.parse("1.0.0-alpha.1+build.11.e0f985a");

        assertEquals("1", v.getMajor().toString());
        assertEquals("0", v.getMinor().toString());
        assertEquals("0", v.getPatch().toString());
        assertTrue(v.isPrerelease());
        assertEquals(Arrays.asList("alpha", "1"), v.getPrerelease());
        assertEquals(Arrays.asList("build", "11", "e0f985a"), v.getBuild());
        assertEquals("1.0.0-alpha.1+build.11.e0f985a", v.toString());
    }

    @Test
    @DisplayName("canonicalization avoids leading zeros in core, but keeps prerelease/build as-is")
    public void testCanonicalization() {
        @NotNull Version v = Version.of(new BigInteger("10"), new BigInteger("0"), new BigInteger("3"), Arrays.asList("rc", "1"), Arrays.asList("exp", "sha", "5114f85"));
        assertEquals("10.0.3-rc.1+exp.sha.5114f85", v.toString());
    }

    @Test
    @DisplayName("SemVer precedence example sequence from the spec")
    public void testSpecOrderingExamples() {
        @NotNull List<String> ordered = Arrays.asList(
                "1.0.0-alpha",
                "1.0.0-alpha.1",
                "1.0.0-alpha.beta",
                "1.0.0-beta",
                "1.0.0-beta.2",
                "1.0.0-beta.11",
                "1.0.0-rc.1",
                "1.0.0"
        );

        @NotNull List<Version> versions = new ArrayList<>();
        for (@NotNull String s : ordered) versions.add(Version.parse(s));

        // verify pairwise that earlier < later
        for (int i = 0; i < versions.size(); i++) {
            for (int j = i + 1; j < versions.size(); j++) {
                int finalI = i;
                int finalJ = j;

                assertTrue(versions.get(i).compareTo(versions.get(j)) < 0,
                        () -> "Expected " + versions.get(finalI) + " < " + versions.get(finalJ));
            }
        }

        // also verify sorting by PRECEDENCE comparator yields the same sequence
        @NotNull List<Version> shuffled = new ArrayList<>(versions);

        Collections.shuffle(shuffled, new Random(12345));
        shuffled.sort(Version.PRECEDENCE);
        assertEquals(versions, shuffled);
    }

    @Test
    @DisplayName("build metadata is ignored for precedence but included in equals/hashCode")
    public void testBuildIgnoredInPrecedenceButIncludedInIdentity() {
        @NotNull Version a = Version.parse("1.0.0+build.1");
        @NotNull Version b = Version.parse("1.0.0+build.2");

        // precedence equal
        assertEquals(0, a.compareTo(b));
        assertEquals(0, Version.PRECEDENCE.compare(a, b));

        // equality should consider build metadata -> not equal
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("PRECEDENCE_WITH_BUILD comparator provides deterministic tiebreaker using build metadata")
    public void testPrecedenceWithBuildComparator() {
        @NotNull Version a = Version.parse("1.0.0+1.2.3");
        @NotNull Version b = Version.parse("1.0.0+1.2.4");
        int res = Version.PRECEDENCE_WITH_BUILD.compare(a, b);
        assertTrue(res < 0, "1.2.3 build should be less than 1.2.4 build");

        // numeric vs lexical builds
        @NotNull Version c = Version.parse("1.0.0+001");
        @NotNull Version d = Version.parse("1.0.0+1");
        // since comparator treats build numbers numerically when both numeric, 1 == 001 numerically,
        // but if implementation treats build numeric differently, at least comparator deterministically orders.
        int cd = Version.PRECEDENCE_WITH_BUILD.compare(c, d);
        // allow either order but ensure comparator is consistent (antisymmetric)
        assertEquals(-Version.PRECEDENCE_WITH_BUILD.compare(d, c), cd);
    }

    @Test
    @DisplayName("invalid core pieces count should throw")
    public void testInvalidCoreCount() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.0"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.0.0.0"));
    }

    @Test
    @DisplayName("non-numeric core components are invalid")
    public void testInvalidCoreNonNumeric() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("a.b.c"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.two.3"));
    }

    @Test
    @DisplayName("leading zeros in core numeric identifiers are invalid")
    public void testInvalidCoreLeadingZeros() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("01.0.0"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.01.0"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.0.001"));
    }

    @Test
    @DisplayName("empty prerelease identifier is invalid (e.g. 1.0.0-)")
    public void testInvalidEmptyPrerelease() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.0.0-"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.0.0-alpha..1"));
    }

    @Test
    @DisplayName("numeric prerelease identifiers must not have leading zeros")
    public void testPrereleaseNumericLeadingZerosInvalid() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.0.0-01"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.0.0-alpha.01"));
    }

    @Test
    @DisplayName("build identifiers accept leading zeros and are validated for allowed characters")
    public void testBuildIdentifiersAllowedLeadingZeros() {
        @NotNull Version v = Version.parse("1.0.0+001.alpha-01");
        assertEquals(Arrays.asList("001", "alpha-01"), v.getBuild());
    }

    @Test
    @DisplayName("invalid characters in identifiers should fail")
    public void testInvalidCharactersInIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.0.0-@beta"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.0.0+build!"));
    }

    @Test
    @DisplayName("bump helpers produce expected versions")
    public void testBumpHelpers() {
        @NotNull Version v = Version.parse("1.2.3-alpha.1+meta");
        @NotNull Version major = v.bumpMajor();
        assertEquals("2.0.0", major.toString());

        @NotNull Version minor = v.bumpMinor();
        assertEquals("1.3.0", minor.toString());

        @NotNull Version patch = v.bumpPatch();
        assertEquals("1.2.4", patch.toString());
    }

    @Test
    @DisplayName("tryParse returns empty optional for invalid input and value for valid")
    public void testTryParse() {
        assertTrue(Version.tryParse("1.0.0").isPresent());
        assertFalse(Version.tryParse("not-a-version").isPresent());
    }

    @Test
    @DisplayName("very large numeric components are supported via BigInteger")
    public void testBigIntegerSupport() {
        @NotNull String big = "123456789012345678901234567890";
        @NotNull String s = big + ".0.0";
        @NotNull Version v = Version.parse(s);

        assertEquals(new BigInteger(big), v.getMajor());
    }

    @Test
    @DisplayName("comparison is consistent with equals for full identity (including build)")
    public void testCompareToConsistentWithEqualsForIdentity() {
        @NotNull Version a = Version.parse("1.2.3+build.1");
        @NotNull Version b = Version.parse("1.2.3+build.1");

        assertEquals(0, a.compareTo(b));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("comparator PRECEDENCE is consistent with compareTo and is transitive")
    public void testPrecedenceComparatorConsistency() {
        @NotNull List<Version> list = Arrays.asList(
                Version.parse("0.1.0"),
                Version.parse("0.1.0-alpha"),
                Version.parse("0.0.9"),
                Version.parse("1.0.0")
        );
        @NotNull List<Version> copy = new ArrayList<>(list);

        copy.sort(Version.PRECEDENCE);
        // check transitive ordering: first < second < third ...
        for (int i = 0; i < copy.size() - 1; i++) {
            assertTrue(copy.get(i).compareTo(copy.get(i + 1)) <= 0);
        }
    }

}