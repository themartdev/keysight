package dev.simonmartineau.keysight.data

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration used for every snapshot column.
 *
 * Defaults are written out so a stored document does not change meaning when a default does,
 * and unknown keys are ignored so a newer app can read what an older one wrote.
 */
val keySightJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
