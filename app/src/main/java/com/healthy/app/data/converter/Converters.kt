package com.healthy.app.data.converter

import androidx.room.TypeConverter

/**
 * `night.editedFields` holds the set of field names the user typed over after
 * a Health Connect sync (spec 3.4). A second sync must not overwrite them.
 *
 * Stored as a comma-separated list because the set is small, is never queried
 * by element in SQL, and stays readable in a CSV or JSON export.
 */
class EditedFieldsConverter {

    @TypeConverter
    fun toSet(raw: String?): Set<String> =
        raw?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toSet()
            .orEmpty()

    @TypeConverter
    fun fromSet(fields: Set<String>?): String =
        fields.orEmpty().filter(String::isNotBlank).sorted().joinToString(",")
}
