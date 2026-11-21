package cz.titulky.tv

import android.content.res.AssetFileDescriptor
import android.media.MediaExtractor
import android.os.SystemClock
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

internal object StickProbe {
    init { System.loadLibrary("stickprobe") }
    external fun open(surface: Surface, width: Int, height: Int): Long
    external fun feed(handle: Long, sample: ByteArray): Boolean
    external fun close(handle: Long)
}

/** The fixture supplies encoded access units at a known rate in place of a PS5 network peer. */
internal class FixtureFrames(file: AssetFileDescriptor, private val fps: Int) {
    private val header: ByteArray
    private val frames = mutableListOf<ByteArray>()
    private val running = AtomicBoolean()
    private var worker: Thread? = null
    private var handle = 0L

    init {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.fileDescriptor, file.startOffset, file.length)
            extractor.selectTrack(0)
            val format = extractor.getTrackFormat(0)
            fun bytes(b: ByteBuffer) = ByteArray(b.remaining()).also { b.get(it) }
            header = bytes(requireNotNull(format.getByteBuffer("csd-0"))) +
                bytes(requireNotNull(format.getByteBuffer("csd-1")))
            val buffer = ByteBuffer.allocate(256 * 1024)
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                buffer.position(0)
                buffer.limit(size)
                val picture = bytes(buffer)
                // Android commonly exposes Annex B. Some MP4 extractors retain 4-byte NAL lengths.
                if (picture.size > 4 && ByteBuffer.wrap(picture).int != 1) {
                    var offset = 0
                    while (offset < picture.size) {
                        val length = ByteBuffer.wrap(picture, offset, 4).int
                        require(length in 1..picture.size - offset - 4)
                        ByteBuffer.wrap(picture, offset, 4).putInt(1)
                        offset += 4 + length
                    }
                }
                frames += picture
                extractor.advance()
            }
            require(frames.isNotEmpty())
        } finally { extractor.release() }
    }

    fun start(surface: Surface, width: Int, height: Int) {
        handle = StickProbe.open(surface, width, height)
        check(handle != 0L) { "Chiaki nemohl otevřít MediaCodec" }
        running.set(true)
        worker = Thread {
            // Decoder input can be temporarily unavailable while MediaCodec is starting.
            while (running.get() && !StickProbe.feed(handle, header)) SystemClock.sleep(10)
            while (running.get() && !StickProbe.feed(handle, frames.first())) SystemClock.sleep(10)
            val first = SystemClock.elapsedRealtime()
            var index = 1
            SystemClock.sleep(1000L / fps)
            while (running.get()) {
                StickProbe.feed(handle, frames[index % frames.size])
                index++
                SystemClock.sleep((first + index * 1000L / fps - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            }
        }.apply { start() }
    }
    fun close() {
        running.set(false)
        worker?.join(5000)
        check(worker?.isAlive != true)
        if (handle != 0L) StickProbe.close(handle)
    }
}
