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
import android.net.Uri;
import android.os.Environment;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.filters.BandPass;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.filters.LowPassFS;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.util.fft.FFT;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, GoogleApiClient.OnConnectionFailedListener{


    private static final String LOG_TAG = MainActivity.class.getName();

    boolean isStarted = false;

    Vibrator vibrator;

    long time; //keeps track of previous measurement's current time measurement
    int value;
    float peak; //keeps track of last heigh of peak

    //================================================================================
    // Audio vars
    //================================================================================
    final int SAMPLE_RATE = 44100;
    final int BUFFER_SIZE = 4096;
    final int CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO;
    final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    final int SECONDS_AUDIO = 5;
    final int BITS_IN_BYTES = 2;
    final int MAX_BYTES = SAMPLE_RATE * BITS_IN_BYTES * SECONDS_AUDIO / (BUFFER_SIZE * 2); //how many byte arrays fit in 10 sec

    final int LOW_PASS_BOUND = 2800;
    final int HIGH_PASS_BOUND = 2000;
    final int BAND_PASS_WIDTH = 1000;
    final int BAND_PASS_FREQ = 2400;
    
    final int LEFT_GAP = 3;
    final int RIGHT_GAP = 3;
    final int PEAK = 3200;
    final double SLOPE1 = 5;
    final double SLOPE2 = 5;
    final int MIN_SIZE = 30;
    final int TARGET_WIDTH = 3;

    ArrayDeque<byte[]> queue = new ArrayDeque<>(); //linked list with previous 10 seconds of audio
    AtomicBoolean audioWaiting = new AtomicBoolean(false);//true means audio should not detect more rings
    AudioDispatcher dispatcher;

    //================================================================================
    // Activity specific constants
    //================================================================================
    final int RC_SIGN_IN = 1;

    private static final String TAG = MainActivity.class.getName();


    //================================================================================
    // UI elements
    //================================================================================

    /*TextView maxText; //displays frequency with highest output
    TextView locText;
    TextView roadText;
    Button start;
    Button stop;*/

    ImageView start;

    //================================================================================
    // geolocation variables
    //================================================================================

    boolean isLocationReady;
    double longitude, latitude; //stores current location
    LocationManager locationManager;
    LocationListener locationListener;

    //================================================================================
    // Authentication/Firebase
    //================================================================================

    GoogleApiClient mGoogleApiClient;

    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private String uid;

    FirebaseStorage storage = FirebaseStorage.getInstance();
    StorageReference storageRef = storage.getReferenceFromUrl("gs://bikebump-ea3b1.appspot.com");
    StorageReference audioRef = storageRef.child("sound_clips_android");




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        start = (ImageView) findViewById(R.id.start_button);

        vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);


        /*
        start = (Button) findViewById(R.id.btnStart);
        stop = (Button) findViewById(R.id.btnStop);
        maxText = (TextView) findViewById(R.id.max_text);
        locText = (TextView) findViewById(R.id.locations_text);
        roadText = (TextView) findViewById(R.id.roads_text); */

        //================================================================================
        // Google Sign In Initialization
        //================================================================================


        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();


        mAuth = FirebaseAuth.getInstance();

        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    uid = user.getUid();
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                }
                // ...
            }
        };


        findViewById(R.id.sign_in_button).setOnClickListener(this);


        checkPermissions();

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isStarted) {
                    dispatcher.stop();
                    isStarted = false;
                    start.setImageResource(R.drawable.start_button);
                }
                else {
                    if (checkPermissions()) {
                        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            buildAlertMessageNoGps();
                        } else {
                            //locText.setText(R.string.locations_searching);
                            locationListener = new MyLocationListener();

                            locationManager.requestLocationUpdates(LocationManager
                                    .GPS_PROVIDER, 3000, 10, locationListener);
                        }
                        recordAudio();
                        isStarted = true;
                        start.setImageResource(R.drawable.started_button);
                    }
                }

            }
        });


        locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);

    }


    //================================================================================
    // Authentication with google sign in
    //================================================================================

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(LOG_TAG, "connection failed");
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sign_in_button:
                signIn();
                break;
            // ...
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
            } else {
                // Google Sign In failed, update UI appropriately
                // ...
            }
        }
    }


    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());
                        uid = task.getResult().getUser().getUid();
                        start.setVisibility(View.VISIBLE);

                        // If sign in fails, display a message to the user. If sign in succeeds
                        // the auth state listener will be notified and logic to handle the
                        // signed in user can be handled in the listener.
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "signInWithCredential", task.getException());
                        }
                        // ...
                    }
                });
    }


    //================================================================================
    // permissions
    //================================================================================


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



    //================================================================================
    // audio stuff
    //================================================================================

    int hzToBin(int hz) {
        return hz * BUFFER_SIZE / SAMPLE_RATE;
    }

    void recordAudio() {

        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, 0);
        dispatcher.addAudioProcessor(new AudioProcessor() {
            // audio save process
            @Override
            public boolean process(AudioEvent audioEvent) {
                byte[] saveBuffer = audioEvent.getByteBuffer().clone();
                queue.add(saveBuffer);
                if (queue.size() > MAX_BYTES + 1) {
                    queue.remove();
                }
                return true;
            }

            @Override
            public void processingFinished() {

            }
        });
        dispatcher.addAudioProcessor(new LowPassFS(LOW_PASS_BOUND, SAMPLE_RATE));
        dispatcher.addAudioProcessor(new HighPass(HIGH_PASS_BOUND, SAMPLE_RATE));
        dispatcher.addAudioProcessor(new BandPass(BAND_PASS_FREQ, BAND_PASS_WIDTH, SAMPLE_RATE));
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
                int peakBin = hzToBin(PEAK);
                /*
                for (int i = peakBin - TARGET_WIDTH; i <= peakBin + TARGET_WIDTH; i++) {
                    if(amplitudes[i] > max) {
                        maxIndex = i;
                        max = amplitudes[i];
                    }
                }*/

                for (int i = 0; i < amplitudes.length; i++) {
                    if(amplitudes[i] > max) {
                        maxIndex = i;
                        max = amplitudes[i];
                    }
                }

                /*if(max > MIN_SIZE) {
                    for (int i = 0; i < amplitudes.length; i++) {

                        Log.d(TAG,Double.toString(fft.binToHz(i, 44100)) + " " + Float.toString(amplitudes[i]));
                    }
                }*/



                //displayMax(fft.binToHz(maxIndex, SAMPLE_RATE), max);
                if(max > MIN_SIZE && Math.abs(maxIndex - hzToBin(PEAK)) < TARGET_WIDTH) {
                    /*for (int i = peakBin - 10; i <= peakBin + 10; i++) {

                    Log.e(TAG, Double.toString(fft.binToHz(maxIndex, 44100)) + " " + Float.toString(amplitudes[i]));
                }*/
                    float s1 = (amplitudes[maxIndex] - amplitudes[maxIndex - LEFT_GAP])/LEFT_GAP;
                    float s2 = (amplitudes[maxIndex] - amplitudes[maxIndex - RIGHT_GAP])/RIGHT_GAP;
                    //Log.e(TAG, Float.toString(s1) + " " + Float.toString(s2) + " " +
                    //        Double.toString(fft.binToHz(maxIndex, 44100)) + " " + max);
                    if(s1 > SLOPE1 && s2 > SLOPE2) {
                        vibrator.vibrate(500);
                        //Log.e(LOG_TAG, "saving audio");
                        if (!audioWaiting.get()) {
                            audioWaiting.set(true);
                            value = 0;
                            time = System.currentTimeMillis();
                            saveAudio();
                            postLocation();
                        }
                        else if(System.currentTimeMillis() - time < 1000 && max > peak + 5) {
                            value = 1;
                        }
                        peak = max;
                    }
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
                //maxText.setText(Double.toString(max) + "hz, magnitude:" + Float.toString(magnitude));
            }
        });
    }

    void saveAudio() {
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(SECONDS_AUDIO * 1000 / 2);
                }
                catch (InterruptedException e) {
                    return;
                }

                try {

                    File wavFile = new File(getFilesDir(), "t=" + time + "_lat=" +
                            latitude + "_lng=" + longitude + "_uid=" + uid + ".wav");
                    String filePath = wavFile.getPath();
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

                    audioWaiting.set(false);

                    Uri sendFile = Uri.fromFile(new File(filePath));
                    StorageReference recordRef = storageRef.child("sound_clips_android/" + sendFile.getLastPathSegment());
                    UploadTask uploadTask = recordRef.putFile(sendFile);

                    File deleteFile = new File(filePath);
                    boolean deleted = deleteFile.delete();
                    Log.d(TAG, Boolean.toString(deleted));

                }
                catch (FileNotFoundException e) {
                    e.printStackTrace();
                }


            }
        };
        t.start();
    }


    //================================================================================
    // geolocation
    //================================================================================



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
            String out = "";
            while (data != -1) {
                char current = (char) data;
                data = isw.read();
                out += current;
            }
            //displayJSON(out);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    void displayJSON(final String s) {

        runOnUiThread(new Runnable() {
            public void run() {
                //roadText.setText(s);
            }
        });
    }

    private class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location loc) {
            longitude = loc.getLongitude();
            latitude = loc.getLatitude();
            isLocationReady = true;
            //locText.setText("long:" + longitude + " lang:" + latitude);
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


    //================================================================================
    // POST Location
    //================================================================================

    public void postLocation() {
        HttpsURLConnection conn = null;

        try {
            URL url = new URL("https://bikebump.media.mit.edu/api/dings/add");
            conn = (HttpsURLConnection) url.openConnection();
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            Uri.Builder builder = new Uri.Builder()
                    .appendQueryParameter("lat", Double.toString(latitude))
                    .appendQueryParameter("lng", Double.toString(longitude))
                    .appendQueryParameter("uid", uid)
                    .appendQueryParameter("timestamp", Long.toString(time))
                    .appendQueryParameter("value", Integer.toString(value));
            String query = builder.build().getEncodedQuery();

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(os, "UTF-8"));
            writer.write(query);
            writer.flush();
            writer.close();
            os.close();

            conn.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
