package dev.simonmartineau.keysight.exercise

import android.content.res.AssetManager
import java.io.InputStream

class AndroidAssetSource(private val assets: AssetManager) : AssetSource {

    override fun list(directory: String): List<String> = assets.list(directory)?.toList().orEmpty()

    override fun open(path: String): InputStream = assets.open(path)
}
