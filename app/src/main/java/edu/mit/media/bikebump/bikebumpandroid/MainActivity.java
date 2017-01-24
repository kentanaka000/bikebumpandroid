package edu.mit.media.bikebump.bikebumpandroid;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
    final int MAX_BYTES = SAMPLE_RATE * 2 * 5 / BUFFER_SIZE; //how many byte arrays fit in 10 sec

    private static final String TAG = MainActivity.class.getName();
    boolean mShouldContinue; //continue recording or not
    TextView maxText; //displays frequency with highest output

    Button start;
    Button stop;
    AudioDispatcher dispatcher;

    LinkedList<byte[]> queue = new LinkedList<>();

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
                mShouldContinue = true;
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
        dispatcher.addAudioProcessor(new LowPassFS(3500, SAMPLE_RATE));
        dispatcher.addAudioProcessor(new HighPass(2500, SAMPLE_RATE));
        dispatcher.addAudioProcessor(new BandPass(3000, 1000, SAMPLE_RATE));
        dispatcher.addAudioProcessor(new AudioProcessor() {

            FFT fft = new FFT(BUFFER_SIZE);
            final float[] amplitudes = new float[BUFFER_SIZE/2];

            @Override
            public boolean process(AudioEvent audioEvent) {
                //save to file

                byte[] saveBuffer = audioEvent.getByteBuffer();
                queue.add(saveBuffer);
                if (queue.size() > MAX_BYTES) {
                    queue.remove();
                }



                //FFT
                float[] audioBuffer = audioEvent.getFloatBuffer();
                fft.forwardTransform(audioBuffer);
                fft.modulus(audioBuffer, amplitudes);

                float max = 0;
                int maxindex = 0;
                for (int i = 0; i < amplitudes.length; i++) {
                    if(amplitudes[i] > max) {
                        maxindex = i;
                        max = amplitudes[i];
                    }
                }
                displayMax(fft.binToHz(maxindex, SAMPLE_RATE), max);


                return true;
            }

            @Override
            public void processingFinished() {

            }
        });

        new Thread(dispatcher,"Audio Dispatcher").start();
    }

    void displayMax(final double max, final float magnitude) {
        runOnUiThread(new Runnable() {
            public void run() {
                maxText.setText(Double.toString(max) + "hz, magnitude:" + Float.toString(magnitude));
            }
        });
    }

}
