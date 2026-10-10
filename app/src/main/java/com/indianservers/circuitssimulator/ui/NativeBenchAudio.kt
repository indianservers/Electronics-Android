package com.indianservers.circuitssimulator.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.indianservers.circuitssimulator.simulation.BenchAudio

internal class NativeBenchAudio {
    private var track:AudioTrack?=null
    fun play(samples:ShortArray) {
        if(samples.isEmpty())return
        val output=track ?: AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(BenchAudio.SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(4096,AudioTrack.getMinBufferSize(BenchAudio.SAMPLE_RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)))
            .build().also { track=it;it.setVolume(.5f);it.play() }
        output.write(samples,0,samples.size,AudioTrack.WRITE_NON_BLOCKING)
    }
    fun close() { track?.let { it.pause();it.flush();it.release() };track=null }
}
