package com.healthy.app.health

import android.content.Context
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import java.time.Instant
import java.time.ZoneId

/**
 * Writes back to Health Connect (spec 3.3).
 *
 * The spec is explicit that a write must happen: keeping weight and fluid only
 * in this app's database would make the data unreadable to everything else the
 * user runs. A failure here never blocks the local save — the app's own row is
 * the record, and the Health Connect copy is a courtesy to other apps.
 */
class HealthWriter(private val context: Context) {

    suspend fun writeWeight(kilograms: Double, at: Long): Boolean = write {
        WeightRecord(
            time = Instant.ofEpochMilli(at),
            zoneOffset = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(at)),
            weight = Mass.kilograms(kilograms),
            metadata = Metadata.manualEntry(),
        )
    }

    /**
     * Health Connect stores hydration in litres and the app shows millilitres
     * (spec 3.3), but the client exposes a millilitre factory, so the value
     * passes through without an arithmetic step that could be got wrong.
     */
    suspend fun writeHydration(millilitres: Int, from: Long, to: Long): Boolean = write {
        HydrationRecord(
            startTime = Instant.ofEpochMilli(from),
            startZoneOffset = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(from)),
            endTime = Instant.ofEpochMilli(to),
            endZoneOffset = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(to)),
            volume = Volume.milliliters(millilitres.toDouble()),
            metadata = Metadata.manualEntry(),
        )
    }

    private suspend fun write(record: () -> androidx.health.connect.client.records.Record): Boolean {
        val client = HealthConnect.client(context) ?: return false
        return runCatching { client.insertRecords(listOf(record())) }.isSuccess
    }

    suspend fun hasWritePermissions(): Boolean =
        HealthConnect.grantedPermissions(context).containsAll(HealthConnect.WRITE_PERMISSIONS)
}
