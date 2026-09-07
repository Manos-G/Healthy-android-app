package com.healthy.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord

/**
 * Health Connect availability and permissions (spec 3.1 and 3.2).
 *
 * Step 4 covers getting permission and telling the user why. Reading the
 * records is step 5.
 */
object HealthConnect {

    /**
     * The three reads the app needs now (spec 3.2).
     *
     * Weight and hydration writes (spec 3.3) are requested in step 7 with the
     * screens that use them, and menstruation only if the user turns that
     * toggle on (spec 10.2). Asking for a permission before the feature exists
     * trains the user to grant things blindly.
     */
    val READ_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        // Spec 3.3 says read weight and hydration as well as write them, so a
        // scale or another app publishing to Health Connect is picked up.
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
    )

    /**
     * Requested only when the user turns cycle tracking on (spec 10.2), which
     * is why they are not in the manifest's always-on set above.
     */
    val MENSTRUATION_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(MenstruationPeriodRecord::class),
        HealthPermission.getWritePermission(MenstruationPeriodRecord::class),
        HealthPermission.getReadPermission(MenstruationFlowRecord::class),
        HealthPermission.getWritePermission(MenstruationFlowRecord::class),
    )

    /** Requested in step 7, listed here so the rationale screen can explain them. */
    val WRITE_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
    )

    enum class Availability {
        /** Installed and usable. */
        Available,

        /** The device supports it but the provider needs installing or updating. */
        NeedsUpdate,

        /** Not supported on this device at all. */
        Unavailable,
    }

    fun availability(context: Context): Availability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> Availability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NeedsUpdate
            else -> Availability.Unavailable
        }

    fun client(context: Context): HealthConnectClient? =
        if (availability(context) == Availability.Available) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else {
            null
        }

    suspend fun grantedPermissions(context: Context): Set<String> =
        client(context)?.permissionController?.getGrantedPermissions().orEmpty()

    suspend fun hasAllReadPermissions(context: Context): Boolean =
        grantedPermissions(context).containsAll(READ_PERMISSIONS)

    fun requestContract() = PermissionController.createRequestPermissionResultContract()

    /** What each permission is for, shown on the rationale screen (spec 3.2). */
    data class Explanation(val title: String, val why: String)

    val EXPLANATIONS: List<Explanation> = listOf(
        Explanation(
            "Sleep",
            "The start time, the end time and the stage blocks of each night. " +
                "The times are what every other number is measured against.",
        ),
        Explanation(
            "Heart rate",
            "Only the samples inside a sleep window. The app takes the 5th percentile " +
                "as your resting rate, and later reads the shape of the night from it.",
        ),
        Explanation(
            "Blood oxygen",
            "The average across a night, when the watch recorded it. " +
                "The field stays empty when it did not.",
        ),
    )
}
