package edu.mit.media.bikebump.bikebumpandroid;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.filters.BandPass;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.filters.LowPassFS;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.util.fft.FFT;

public class MainActivity extends AppCompatActivity {


    private static final String LOG_TAG = MainActivity.class.getName();

    final int SAMPLE_RATE = 44100;
    final int BUFFER_SIZE = 4096;
    final int CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO;
    final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    final int SECONDS_AUDIO = 10;
    final int BITS_IN_BYTES = 2;
    final int MAX_BYTES = SAMPLE_RATE * BITS_IN_BYTES * SECONDS_AUDIO / (BUFFER_SIZE * 2); //how many byte arrays fit in 10 sec
    int counter = 0;

    private static final String TAG = MainActivity.class.getName();
    boolean mShouldContinue; //continue recording or not
    TextView maxText; //displays frequency with highest output

    Button start;
    Button stop;
    AudioDispatcher dispatcher;

    //linkedlist
    ArrayDeque<byte[]> queue = new ArrayDeque<>();
    AtomicBoolean save = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        start = (Button) findViewById(R.id.btnStart);
        stop = (Button) findViewById(R.id.btnStop);
        maxText = (TextView) findViewById(R.id.max_text);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {


            // No explanation needed, we can request the permission.

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO,},
                    0);

            // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        0);
        }

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordAudio();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatcher.stop();
            }
        });

    }

    void recordAudio() {

        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, 0);
        dispatcher.addAudioProcessor(new AudioProcessor() {
            // audio save process
            @Override
            public boolean process(AudioEvent audioEvent) {
                byte[] saveBuffer = audioEvent.getByteBuffer().clone();
                queue.add(saveBuffer);
                Log.d(LOG_TAG, Integer.toString(queue.size()));
                if (queue.size() > MAX_BYTES && !save.get()) {
                    queue.remove();
                }
                return true;
            }

            @Override
            public void processingFinished() {

            }
        });
        dispatcher.addAudioProcessor(new LowPassFS(3500, SAMPLE_RATE));
        dispatcher.addAudioProcessor(new HighPass(2500, SAMPLE_RATE));
        dispatcher.addAudioProcessor(new BandPass(3000, 1000, SAMPLE_RATE));
        dispatcher.addAudioProcessor(new AudioProcessor() {

            FFT fft = new FFT(BUFFER_SIZE);
            final float[] amplitudes = new float[BUFFER_SIZE/2];

            @Override
            public boolean process(AudioEvent audioEvent) {

                //FFT
                float[] audioBuffer = audioEvent.getFloatBuffer();
                fft.forwardTransform(audioBuffer);
                fft.modulus(audioBuffer, amplitudes);

                float max = 0;
                int maxIndex = 0;
                for (int i = 0; i < amplitudes.length; i++) {
                    if(amplitudes[i] > max) {
                        maxIndex = i;
                        max = amplitudes[i];
                    }
                }
                displayMax(fft.binToHz(maxIndex, SAMPLE_RATE), max);

                if (Math.abs(fft.binToHz(maxIndex, SAMPLE_RATE) - 3000) < 200 && max > 30 && !save.get()) {
                    Log.d(LOG_TAG, "saving audio");
                    saveAudio();
                }


                return true;
            }

            @Override
            public void processingFinished() {

            }
        });

        new Thread(dispatcher,"Audio Dispatcher").start();
    }

    void saveAudio() {
        // used https://gist.github.com/kmark/d8b1b01fb0d2febf5770

        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(4000);
                }
                catch (InterruptedException e) {
                    return;
                }
                save.set(true);
                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e) {
                    return;
                }

                try {

                    File wavFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "recording_" + (System.currentTimeMillis() / 1000 - 5) + ".wav");
                    FileOutputStream wavOut = new FileOutputStream(wavFile);
                    try {

                        // Write out the wav file header
                        writeWavHeader(wavOut, CHANNEL_MASK, SAMPLE_RATE, ENCODING);
                        int counter = 0;
                        while (counter < MAX_BYTES) {
                            counter++;
                            wavOut.write(queue.remove(), 0, BUFFER_SIZE*2);

                        }
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    finally {
                        if (wavOut != null) {
                            try {
                                wavOut.close();
                            } catch (IOException ex) {
                                //
                            }
                        }
                    }


                    try {
                        // This is not put in the try/catch/finally above since it needs to run
                        // after we close the FileOutputStream
                        updateWavHeader(wavFile);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    save.set(false);

                }
                catch (FileNotFoundException e) {
                    e.printStackTrace();
                }


            }
        };
        t.start();
    }
    void displayMax(final double max, final float magnitude) {

        runOnUiThread(new Runnable() {
            public void run() {
                maxText.setText(Double.toString(max) + "hz, magnitude:" + Float.toString(magnitude));
                counter ++;
                Log.d(LOG_TAG, "counter" +counter);
            }
        });
    }


    /**
     * Writes the proper 44-byte RIFF/WAVE header to/for the given stream
     * Two size fields are left empty/null since we do not yet know the final stream size
     *
     * @param out         The stream to write the header to
     * @param channelMask An AudioFormat.CHANNEL_* mask
     * @param sampleRate  The sample rate in hertz
     * @param encoding    An AudioFormat.ENCODING_PCM_* value
     * @throws IOException
     */


    private static void writeWavHeader(OutputStream out, int channelMask, int sampleRate, int encoding) throws IOException {
        short channels;
        switch (channelMask) {
            case AudioFormat.CHANNEL_IN_MONO:
                channels = 1;
                break;
            case AudioFormat.CHANNEL_IN_STEREO:
                channels = 2;
                break;
            default:
                throw new IllegalArgumentException("Unacceptable channel mask");
        }

        short bitDepth;
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_8BIT:
                bitDepth = 8;
                break;
            case AudioFormat.ENCODING_PCM_16BIT:
                bitDepth = 16;
                break;
            case AudioFormat.ENCODING_PCM_FLOAT:
                bitDepth = 32;
                break;
            default:
                throw new IllegalArgumentException("Unacceptable encoding");
        }

        writeWavHeader(out, channels, sampleRate, bitDepth);
    }


    /**
     * Writes the proper 44-byte RIFF/WAVE header to/for the given stream
     * Two size fields are left empty/null since we do not yet know the final stream size
     *
     * @param out        The stream to write the header to
     * @param channels   The number of channels
     * @param sampleRate The sample rate in hertz
     * @param bitDepth   The bit depth
     * @throws IOException
     */


    private static void writeWavHeader(OutputStream out, short channels, int sampleRate, short bitDepth) throws IOException {
        // Convert the multi-byte integers to raw bytes in little endian format as required by the spec
        byte[] littleBytes = ByteBuffer
                .allocate(14)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(channels)
                .putInt(sampleRate)
                .putInt(sampleRate * channels * (bitDepth / 8))
                .putShort((short) (channels * (bitDepth / 8)))
                .putShort(bitDepth)
                .array();

        // Not necessarily the best, but it's very easy to visualize this way
        out.write(new byte[]{
                // RIFF header
                'R', 'I', 'F', 'F', // ChunkID
                0, 0, 0, 0, // ChunkSize (must be updated later)
                'W', 'A', 'V', 'E', // Format
                // fmt subchunk
                'f', 'm', 't', ' ', // Subchunk1ID
                16, 0, 0, 0, // Subchunk1Size
                1, 0, // AudioFormat
                littleBytes[0], littleBytes[1], // NumChannels
                littleBytes[2], littleBytes[3], littleBytes[4], littleBytes[5], // SampleRate
                littleBytes[6], littleBytes[7], littleBytes[8], littleBytes[9], // ByteRate
                littleBytes[10], littleBytes[11], // BlockAlign
                littleBytes[12], littleBytes[13], // BitsPerSample
                // data subchunk
                'd', 'a', 't', 'a', // Subchunk2ID
                0, 0, 0, 0, // Subchunk2Size (must be updated later)
        });
    }


    /**
     * Updates the given wav file's header to include the final chunk sizes
     *
     * @param wav The wav file to update
     * @throws IOException
     */


    private static void updateWavHeader(File wav) throws IOException {
        byte[] sizes = ByteBuffer
                .allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                // There are probably a bunch of different/better ways to calculate
                // these two given your circumstances. Cast should be safe since if the WAV is
                // > 4 GB we've already made a terrible mistake.
                .putInt((int) (wav.length() - 8)) // ChunkSize
                .putInt((int) (wav.length() - 44)) // Subchunk2Size
                .array();

        RandomAccessFile accessWave = null;
        //noinspection CaughtExceptionImmediatelyRethrown
        try {
            accessWave = new RandomAccessFile(wav, "rw");
            // ChunkSize
            accessWave.seek(4);
            accessWave.write(sizes, 0, 4);

            // Subchunk2Size
            accessWave.seek(40);
            accessWave.write(sizes, 4, 4);
        } catch (IOException ex) {
            // Rethrow but we still close accessWave in our finally
            throw ex;
        } finally {
            if (accessWave != null) {
                try {
                    accessWave.close();
                } catch (IOException ex) {
                    //
                }
            }
        }
    }


}
