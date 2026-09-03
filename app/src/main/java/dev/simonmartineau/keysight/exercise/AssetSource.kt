package dev.simonmartineau.keysight.exercise

import java.io.InputStream

/** Read access to bundled files, abstracted so the content pack can be validated on the JVM. */
interface AssetSource {

    /** File names directly under [directory], in no particular order. */
    fun list(directory: String): List<String>

    fun open(path: String): InputStream
}
