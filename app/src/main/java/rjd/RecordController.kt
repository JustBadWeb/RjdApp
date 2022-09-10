package rjd

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class RecordController(private val context: Context) {
    private var audioRecorder: MediaRecorder? = null
    var recordedFile: File? = null

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createFile(): File? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())

        // Creating directory Speech in music
        val f = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "/Speech"
        )
        if (!f.exists())
            f.mkdir()

        val storageDir = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC
            ), "Speech"
        )

        recordedFile = File.createTempFile(
            "M4A_${timeStamp}_",
            ".m4a",
            storageDir
        )
        return recordedFile
    }

    fun start() {
        Log.d(TAG, "Start")

        audioRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(createFile()!!.absolutePath)
            setAudioChannels(1)
            prepare()
            start()
        }
    }

    fun stop() {
        audioRecorder?.let {
            Log.d(TAG, "Stop")
            it.stop()
            it.release()
        }
        audioRecorder = null
    }

    fun isAudioRecording() = audioRecorder != null

    fun getVolume() = audioRecorder?.maxAmplitude ?: 0

    @JvmName("getMyRecordedFile")
    fun getRecordedFile(): File {
        return recordedFile!!
    }

    private companion object {
        private val TAG = RecordController::class.java.name
    }
}