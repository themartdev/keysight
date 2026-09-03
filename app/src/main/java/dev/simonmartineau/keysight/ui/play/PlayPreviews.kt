package dev.simonmartineau.keysight.ui.play

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.exercise.Accompaniment
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.history.RunDigest
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.settings.ContentConfig
import dev.simonmartineau.keysight.settings.NotesLadder
import dev.simonmartineau.keysight.ui.theme.KeySightTheme

private val noActions = PlayActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

private const val NOW = 1_788_400_000_000L

private val lastRun = RunDigest("r", "s", NOW - 90_000_000L, RunConfig.DEFAULT, KeySignature.C_MAJOR, Hands.RIGHT, 8, emptyList())

@Composable
private fun PreviewPlay(
    config: RunConfig = RunConfig.DEFAULT.copy(mode = VisibilityMode.READ_AHEAD),
    content: ContentConfig = ContentConfig(KeySignature.C_MAJOR, Hands.RIGHT),
    adaptEnabled: Boolean = false,
    level: MusicalLevel? = content.level,
    connection: MidiConnection = MidiConnection.Connected("Roland FP-30X"),
    dark: Boolean = false,
) {
    KeySightTheme(darkTheme = dark) {
        PlayContent(
            connection = connection,
            config = config,
            content = content,
            adaptEnabled = adaptEnabled,
            level = level,
            lastRun = lastRun,
            previewSeed = 7L,
            now = NOW,
            actions = noActions,
        )
    }
}

@Preview(name = "Play, phone, read ahead", showBackground = true, widthDp = 764, heightDp = 366)
@Composable
private fun PlayPhonePreview() = PreviewPlay()

@Preview(name = "Play, tablet, flash, both hands, adapting", showBackground = true, widthDp = 1200, heightDp = 776)
@Composable
private fun PlayTabletPreview() = PreviewPlay(
    config = RunConfig.DEFAULT,
    content = ContentConfig(KeySignature(2), Hands.BOTH, Accompaniment.HELD_NOTE),
    adaptEnabled = true,
    level = NotesLadder.LEVELS[3],
)

@Preview(name = "Play, phone, flash, level by hand", showBackground = true, widthDp = 764, heightDp = 366)
@Composable
private fun PlayFlashByHandPreview() = PreviewPlay(config = RunConfig.DEFAULT, content = ContentConfig(KeySignature(-1), Hands.LEFT, level = NotesLadder.LEVELS[5]))

@Preview(name = "Play, no keyboard, dark", showBackground = true, widthDp = 764, heightDp = 366, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlayNoKeyboardPreview() = PreviewPlay(connection = MidiConnection.NoDevice, dark = true)
