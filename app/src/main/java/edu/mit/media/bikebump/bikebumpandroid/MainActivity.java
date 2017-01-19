package edu.mit.media.bikebump.bikebumpandroid;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {


    private static final String LOG_TAG = MainActivity.class.getName();

    final int SAMPLE_RATE = 44100;
    boolean mShouldContinue; //continue recording or not
    TextView maxText; //displays frequency with highest output

    Button start;
    Button stop;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        start = (Button)findViewById(R.id.btnStart);
        stop = (Button)findViewById(R.id.btnStop);
        maxText = (TextView)findViewById(R.id.max_text);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {


            // No explanation needed, we can request the permission.

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    0);

            // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        }

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mShouldContinue = true;
                    recordAudio();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mShouldContinue) {
                    mShouldContinue = false;
                }
            }
        });

    }



    void recordAudio() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "started");
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

                // buffer size in bytes
                int bufferSize = getLargerPower2(AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO,
                 AudioFormat.ENCODING_PCM_16BIT))/2;

                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    bufferSize = getLargerPower2(SAMPLE_RATE);
                }
                FFT fft = new FFT(bufferSize);

                short[] audioBuffer = new short[bufferSize];

                AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);
                Log.i(LOG_TAG, Integer.toString(bufferSize));

                if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(LOG_TAG, "Audio Record can't initialize!");
                    return;
                }
                record.startRecording();

                Log.v(LOG_TAG, "Start recording");

                long shortsRead = 0;
                while (mShouldContinue) {
                    int numberOfShort = record.read(audioBuffer, 0, audioBuffer.length);
                    shortsRead += numberOfShort;
                    double[] complex = new double[bufferSize];
                    double[] real = new double[bufferSize];

                    //set real to audio reading and complex to 0s
                    for(int i = 0; i < bufferSize ; i++) {
                        complex[i] = 0;
                        real[i] = ((double)audioBuffer[i])/Short.MAX_VALUE;
                    }
                    fft.fft(real, complex);

                    double[] result = new double[bufferSize];
                    for (int i = 0; i < bufferSize; i++) {
                        result[i] = real[i] * real[i] + complex[i] * complex[i];
                    }

                    double max = result[0];
                    int index = 0;
                    for (int i = 0; i < bufferSize / 2; i++) {
                        if (result[i] > max) {
                            max = result[i];
                            index = i;
                        }
                    }
                    displayMax(index*SAMPLE_RATE/bufferSize);

                }

                record.stop();
                record.release();

                Log.v(LOG_TAG, String.format("Recording stopped. Samples read: %d", shortsRead));
           }
        }).start();
    }

    void displayMax(final double max) {
        runOnUiThread(new Runnable() {
            public void run() {
                maxText.setText(Double.toString(max));
            }
        });
    }

    int getLargerPower2(int n) {
        int result = 1;
        while (result < n) {
            result *= 2;
        }
        return result;
    }


}
