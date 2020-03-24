/*
 * Copyright (C) 2019-2020 Philip Jones
 *
 * Licensed under the MIT License.
 * See either the LICENSE file, or:
 *
 * https://opensource.org/licenses/MIT
 *
 */

package com.philj56.gbcc

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SaveBackupAgent : BackupAgent() {
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) {
    }

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) {
    }

    override fun onFullBackup(data: FullBackupDataOutput?) {
        val zipfile = File(getExternalFilesDir(null), "saves.zip")
        val zip = ZipOutputStream(FileOutputStream(zipfile))
        zip.setMethod(ZipOutputStream.DEFLATED)
        zip.setLevel(9)
        getExternalFilesDir(null)?.walk()?.forEach {file ->
            if (file.extension == "sav") {
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().copyTo(zip)
                zip.closeEntry()
            }
        }
        zip.close()
        fullBackupFile(zipfile, data)
    }

    override fun onRestoreFile(
        data: ParcelFileDescriptor?,
        size: Long,
        destination: File?,
        type: Int,
        mode: Long,
        mtime: Long
    ) {
        if (data != null && destination != null) {
            when(type) {
                TYPE_DIRECTORY -> destination.mkdirs()
                TYPE_FILE -> destination.outputStream().use { output ->
                    val buffer = ByteArray(size.toInt())
                    ParcelFileDescriptor.AutoCloseInputStream(data).use {
                        it.read(buffer, 0, size.toInt())
                    }
                    output.write(buffer)
                }
            }

            if (destination.extension == "zip") {
                val zip = ZipInputStream(destination.inputStream())
                var entry: ZipEntry
                while (zip.nextEntry.also { entry = it } != null) {
                    val file = File(getExternalFilesDir(null), entry.name)
                    file.outputStream().use { zip.copyTo(it) }
                    zip.closeEntry()
                }
                zip.close()
            }
        }
    }
}