package com.deepanjanxyz.notepad.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileNamesTest {

    @Test
    fun testTheBackupFileItselfIsOurs() {
        assertTrue(BackupFileNames.isOurBackupName("elite-memo-backup.json"))
    }

    @Test
    fun testWorkingFilesAreOurs() {
        assertTrue(BackupFileNames.isOurBackupName("elite-memo-backup.partial.json"))
        assertTrue(BackupFileNames.isOurBackupName("elite-memo-backup.previous.json"))
    }

    @Test
    fun testNumberedCopiesAreOurs() {
        assertTrue(BackupFileNames.isOurBackupName("elite-memo-backup (1).json"))
        assertTrue(BackupFileNames.isOurBackupName("elite-memo-backup (18).json"))
        assertTrue(BackupFileNames.isOurBackupName("elite-memo-backup.partial (9).json"))
        assertTrue(BackupFileNames.isOurBackupName("elite-memo-backup.previous (3).json"))
    }

    @Test
    fun testOtherFilesAreNotOurs() {
        assertFalse(BackupFileNames.isOurBackupName("notes.json"))
        assertFalse(BackupFileNames.isOurBackupName("elite-memo-backup"))
        assertFalse(BackupFileNames.isOurBackupName("elite-memo-backup copy.json"))
        assertFalse(BackupFileNames.isOurBackupName("elite-memo-backup (x).json"))
        assertFalse(BackupFileNames.isOurBackupName("elite-memo-backup (1).txt"))
    }

    @Test
    fun testWorkingNamesKeepTheJsonSuffix() {
        assertTrue(BackupFileNames.partialName("elite-memo-backup.json").endsWith(".json"))
        assertTrue(BackupFileNames.previousName("elite-memo-backup.json").endsWith(".json"))
        assertFalse(BackupFileNames.isOurBackupName(BackupFileNames.partialName("notes.json")))
    }
}
