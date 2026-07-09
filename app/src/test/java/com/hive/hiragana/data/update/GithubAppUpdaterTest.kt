package com.hive.hiragana.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the release-tag vs installed-version comparison the updater gates on. */
class GithubAppUpdaterTest {

    @Test
    fun newerPatchIsNewer() {
        assertTrue(GithubAppUpdater.isNewer(remote = "v0.1.2", current = "0.1.1"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(GithubAppUpdater.isNewer(remote = "v0.1.1", current = "0.1.1"))
        // A missing trailing component counts as zero (0.1.1 == 0.1.1.0).
        assertFalse(GithubAppUpdater.isNewer(remote = "0.1.1", current = "0.1.1.0"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(GithubAppUpdater.isNewer(remote = "v0.1.0", current = "0.1.1"))
    }

    @Test
    fun leadingVPrefixIsIgnored() {
        assertTrue(GithubAppUpdater.isNewer(remote = "v1.0.0", current = "0.9.9"))
    }

    @Test
    fun comparisonIsNumericNotLexical() {
        // "10" > "9" numerically, even though it sorts before it as text.
        assertTrue(GithubAppUpdater.isNewer(remote = "v0.1.10", current = "0.1.9"))
    }

    @Test
    fun higherMinorBeatsLowerPatch() {
        assertTrue(GithubAppUpdater.isNewer(remote = "v0.2", current = "0.1.9"))
    }
}
