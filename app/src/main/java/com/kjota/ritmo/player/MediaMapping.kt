package com.kjota.ritmo.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.kjota.ritmo.data.Track

fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri("ritmo:$id")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(Uri.parse(thumbHq))
                .setExtras(Bundle().apply { putInt("dur", durationSec) })
                .build()
        )
        .build()

fun MediaItem.toTrack(): Track =
    Track(
        id = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        durationSec = mediaMetadata.extras?.getInt("dur") ?: 0,
    )
