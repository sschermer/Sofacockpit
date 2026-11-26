package de.tonmaschine.android.storage

import de.tonmaschine.android.model.Catalog
import de.tonmaschine.android.model.ProfileCodec
import de.tonmaschine.android.model.SoundProfile
import java.io.IOException
import org.junit.Test
import org.junit.Assert.*

class SharedProfileTransferTest {
    private val profile = SoundProfile(7, Catalog.levels.associate { it.field to 0 }, false, true, true)
    private class MemoryFiles(var raw: String) : RemoteProfileFiles {
        var locked = false
        var writes = 0
        override fun ensureFolder() {}
        override fun read(name: String): String? = raw
        override fun tryLock(): Boolean {
            if (locked) return false
            locked = true
            return true
        }
        override fun unlock() { locked = false }
        override fun writeAtomically(name: String, text: String) {
            check(locked)
            writes++
            raw = text
        }
        override fun close() {}
    }
    @Test fun copiesThenRenamesWithoutChangingValues() {
        val files = MemoryFiles(ProfileCodec.encode(mapOf("Kino" to profile)))
        val repo = SharedProfileRepository(files)
        assertEquals(mapOf("Kino" to profile, "Kopie" to profile), repo.transfer("Kino", "Kopie", false))
        assertEquals(mapOf("Kino" to profile, "Neu" to profile), repo.transfer("Kopie", "Neu", true))
        assertEquals(2, files.writes)
        assertFalse(files.locked)
    }
    @Test fun refusesExistingTargetWithoutWriting() {
        val original = ProfileCodec.encode(mapOf("Kino" to profile, "Musik" to profile))
        val files = MemoryFiles(original)
        val repo = SharedProfileRepository(files)
        try {
            repo.transfer("Kino", "Musik", true)
            fail("Existing target must not be overwritten")
        } catch (_: IOException) {}
        assertEquals(original, files.raw)
        assertEquals(0, files.writes)
        assertFalse(files.locked)
    }
}
