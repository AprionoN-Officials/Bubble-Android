package com.bubble.screentools.update

/**
 * Pure-Kotlin Semantic Versioning parser and comparator.
 * Handles optional 'v'/'V' prefixes, numeric segments, prerelease identifiers, and build metadata.
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String = ""
) : Comparable<SemVer> {

    val isPrerelease: Boolean get() = prerelease.isNotEmpty()

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        // SemVer 2.0.0: a normal release has higher precedence than a prerelease of the same version
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
        if (prerelease.isEmpty()) return 1
        if (other.prerelease.isEmpty()) return -1

        val aParts = prerelease.split(".")
        val bParts = other.prerelease.split(".")
        val count = maxOf(aParts.size, bParts.size)
        for (i in 0 until count) {
            val a = aParts.getOrNull(i) ?: return -1
            val b = bParts.getOrNull(i) ?: return 1
            val aNum = a.toIntOrNull()
            val bNum = b.toIntOrNull()
            val cmp = when {
                aNum != null && bNum != null -> aNum.compareTo(bNum)
                aNum != null -> -1 // Numeric identifiers always have lower precedence than non-numeric
                bNum != null -> 1
                else -> a.compareTo(b)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    override fun toString(): String {
        val base = "$major.$minor.$patch"
        return if (prerelease.isNotEmpty()) "$base-$prerelease" else base
    }

    companion object {
        private val SEMVER_REGEX = Regex(
            "^[vV]?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$"
        )

        fun parse(raw: String): SemVer? {
            val trimmed = raw.trim()
            val match = SEMVER_REGEX.matchEntire(trimmed) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return null
            val patch = match.groupValues[3].ifEmpty { "0" }.toIntOrNull() ?: return null
            val pre = match.groupValues[4]
            return SemVer(major, minor, patch, pre)
        }

        /**
         * Returns true ONLY if [candidate] is strictly newer than [installed] and respects
         * the [includePrerelease] preference (never permits accidental downgrades).
         */
        fun isUpdateAvailable(
            installedVersion: String,
            candidateTag: String,
            candidateIsPrereleaseFlag: Boolean,
            includePrerelease: Boolean
        ): Boolean {
            val installed = parse(installedVersion) ?: return false
            val candidate = parse(candidateTag) ?: return false
            val isPre = candidateIsPrereleaseFlag || candidate.isPrerelease
            if (isPre && !includePrerelease) return false
            return candidate > installed
        }
    }
}
