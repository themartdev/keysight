package dev.simonmartineau.keysight.exercise

import java.io.File
import java.io.InputStream

/** Reads the content pack from the source tree, so it can be validated without a device. */
class FileAssetSource(private val root: File) : AssetSource {

    override fun list(directory: String): List<String> = File(root, directory).list()?.toList().orEmpty()

    override fun open(path: String): InputStream = File(root, path).inputStream()
}
