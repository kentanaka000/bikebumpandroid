package edu.mit.media.bikebump.bikebumpandroid;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayDeque;
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
    double longitude, latitude;

    private static final String TAG = MainActivity.class.getName();
    boolean mShouldContinue; //continue recording or not
    TextView maxText; //displays frequency with highest output
    TextView locText;
    TextView roadText;

    Button start;
    Button stop;
    AudioDispatcher dispatcher;
    LocationManager locationManager;
    LocationListener locationListener;

    //linkedlist
    ArrayDeque<byte[]> queue = new ArrayDeque<>();
    AtomicBoolean save = new AtomicBoolean(false);//is currently saving or not

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        start = (Button) findViewById(R.id.btnStart);
        stop = (Button) findViewById(R.id.btnStop);
        maxText = (TextView) findViewById(R.id.max_text);
        locText = (TextView) findViewById(R.id.locations_text);
        roadText = (TextView) findViewById(R.id.roads_text);

        checkPermissions();

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermissions()) {
                    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        buildAlertMessageNoGps();
                    }
                    else {
                        locText.setText(R.string.locations_searching);
                        locationListener = new MyLocationListener();

                        locationManager.requestLocationUpdates(LocationManager
                                .GPS_PROVIDER, 3000, 10, locationListener);
                    }
                    recordAudio();
                }

            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatcher.stop();
            }
        });


        locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);

    }

    boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this,
                Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.INTERNET,
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    0);

            return false;
        }
        return true;
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }


    void recordAudio() {

        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, 0);
        dispatcher.addAudioProcessor(new AudioProcessor() {
            // audio save process
            @Override
            public boolean process(AudioEvent audioEvent) {
                byte[] saveBuffer = audioEvent.getByteBuffer().clone();
                queue.add(saveBuffer);
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

                if (Math.abs(fft.binToHz(maxIndex, SAMPLE_RATE) - 3000) < 200 && max > 30
                            && queue.size() > MAX_BYTES - 4 && !save.get()) {
                    Log.d(LOG_TAG, "saving audio");
                    saveAudio();
                    requestStreet();
                }


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

    void saveAudio() {

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

                    File wavFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "recording_" + (System.currentTimeMillis() / 1000 - 5) + ".wav");
                    FileOutputStream wavOut = new FileOutputStream(wavFile);
                    try {

                        // Write out the wav file header
                        WAVWriter.writeWavHeader(wavOut, CHANNEL_MASK, SAMPLE_RATE, ENCODING);
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
                        WAVWriter.updateWavHeader(wavFile);
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


    public void requestStreet() {
        URL url;
        HttpURLConnection urlConnection = null;
        try {
            url = new URL("https://bikebump.media.mit.edu/api/road/closest?lng=" + longitude +
                    "&lat=" + latitude);

            urlConnection = (HttpURLConnection) url
                    .openConnection();

            InputStream in = urlConnection.getInputStream();

            InputStreamReader isw = new InputStreamReader(in);

            int data = isw.read();
            while (data != -1) {
                char current = (char) data;
                data = isw.read();
                System.out.print(current);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }



    /*----------Listener class to get coordinates ------------- */
    private class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location loc) {
            longitude = loc.getLongitude();
            latitude = loc.getLatitude();
            locText.setText("long:" + longitude + " lang:" + latitude);
        }

        @Override
        public void onProviderDisabled(String provider) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onProviderEnabled(String provider) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onStatusChanged(String provider,
                                    int status, Bundle extras) {
            // TODO Auto-generated method stub
        }
    }


}
