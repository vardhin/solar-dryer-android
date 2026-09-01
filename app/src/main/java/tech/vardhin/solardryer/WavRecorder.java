package tech.vardhin.solardryer;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicBoolean;

public class WavRecorder {
    private static final int SAMPLE_RATE = 16000;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private AudioRecord audioRecord;
    private Thread worker;
    private File outputFile;

    public boolean isRecording() { return recording.get(); }

    public void start(File file) throws Exception {
        if (recording.get()) return;
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(min, 4096);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("Microphone could not be initialized");
        }
        outputFile = file;
        writeHeader(outputFile);
        recording.set(true);
        audioRecord.startRecording();
        worker = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            try (FileOutputStream out = new FileOutputStream(outputFile, true)) {
                while (recording.get()) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) out.write(buffer, 0, read);
                }
            } catch (Exception ignored) { }
        }, "wav-recorder");
        worker.start();
    }

    public File stop() throws Exception {
        if (!recording.get()) return outputFile;
        recording.set(false);
        try { audioRecord.stop(); } catch (Exception ignored) { }
        if (worker != null) worker.join(1500);
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
        finishHeader(outputFile);
        return outputFile;
    }

    public void cancel() {
        try { stop(); } catch (Exception ignored) { }
        if (outputFile != null) outputFile.delete();
    }

    private static void writeHeader(File file) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            byte[] header = new byte[44];
            header[0]='R'; header[1]='I'; header[2]='F'; header[3]='F';
            header[8]='W'; header[9]='A'; header[10]='V'; header[11]='E';
            header[12]='f'; header[13]='m'; header[14]='t'; header[15]=' ';
            putInt(header,16,16);
            putShort(header,20,(short)1);
            putShort(header,22,(short)1);
            putInt(header,24,SAMPLE_RATE);
            putInt(header,28,SAMPLE_RATE*2);
            putShort(header,32,(short)2);
            putShort(header,34,(short)16);
            header[36]='d'; header[37]='a'; header[38]='t'; header[39]='a';
            out.write(header);
        }
    }

    private static void finishHeader(File file) throws Exception {
        long dataSize = Math.max(0, file.length() - 44);
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(4); writeIntLE(raf, (int)(36 + dataSize));
            raf.seek(40); writeIntLE(raf, (int)dataSize);
        }
    }

    private static void putInt(byte[] b, int o, int v) {
        b[o]=(byte)v; b[o+1]=(byte)(v>>8); b[o+2]=(byte)(v>>16); b[o+3]=(byte)(v>>24);
    }
    private static void putShort(byte[] b, int o, short v) {
        b[o]=(byte)v; b[o+1]=(byte)(v>>8);
    }
    private static void writeIntLE(RandomAccessFile raf, int v) throws Exception {
        raf.write(v); raf.write(v>>8); raf.write(v>>16); raf.write(v>>24);
    }
}
