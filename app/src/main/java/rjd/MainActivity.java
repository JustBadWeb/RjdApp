package rjd;

import android.annotation.SuppressLint;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.media.audiofx.NoiseSuppressor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.yalantis.ucrop.BuildConfig;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.ByteString;

public final class MainActivity extends AppCompatActivity {
    private Bitmap toRecognize;
    private final int REQUEST_CODE_PICK_IMAGE = 100;
    private final int REQUEST_CODE_MAKE_IMAGE = 200;

    private String currentPhotoPath;
    private RecordController recordController;
    private ImageView recordVoiceView;
    private ImageView resultImageView;
    private TextView resultNumberView;
    private TextView resultDefectView;
    private EditText idCompView;

    private OkHttpClient client;
    ResponseBody responseBody;

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return value;
    }

    private void setText(final TextView text, final String value){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                text.setText(value);
            }
        });
    }

    public Boolean uploadFile(String serverURL, File file, String name, String mimeType) {
        try {
            RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", name,
                            RequestBody.create(MediaType.parse(mimeType), file))
                    .build();

            Request request = new Request.Builder()
                    .url(serverURL)
                    .post(requestBody)
                    .build();

            client.newCall(request)
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull final Call call, @NonNull IOException e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // For the example, you can show an error dialog or a toast
                                    // on the main UI thread
                                }
                            });
                        }

                        @SuppressLint("NewApi")
                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            assert response.body() != null;
                            responseBody = response.body();
                            boolean isString = ("Yes".equals(response.headers().get("IsString")));
                            if (isString) {
                                String HexResult = response.headers().get("Result");
                                if (Objects.equals(HexResult, "")) {
                                    setText(resultDefectView, "Текст не распознан");
                                }
                                else {
                                    List<String> hexStringList = Arrays.asList(Objects.requireNonNull(HexResult).split(" "));
                                    List<String> strings = new ArrayList<>();
                                    hexStringList.forEach((String hexString) -> {
                                        strings.add(ByteString.decodeHex(hexString).toString());
                                    });
                                    String result = String.join(" ", strings).replace("[text=", "").replace("]", "");
                                    setText(resultDefectView, result);
                                }
                            }
                            else {
                                setText(resultNumberView, response.headers().get("Result"));
                            }
                        }
                    });

            return true;
        } catch (Exception ex) {
            Log.d("ERROR RESPONCE", ex.toString());
        }
        return false;
    }

    @SuppressLint("InlinedApi")
    @RequiresApi(23)
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rjd_activity_main);

        //

        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.INTERNET},
                1);

        // HTTP

        client = new OkHttpClient();
        idCompView = findViewById(R.id.compIp);
        idCompView.setText("192.168.11.13");

        // photo

        ImageView makePhotoView = findViewById(R.id.makeImage);
        makePhotoView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                    photo();
                }
            }
        });

        ImageView loadFromGalleryView = findViewById(R.id.chooseFromGallery);
        loadFromGalleryView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                    openImagesDocument();
                }
            }
        });
        resultImageView = findViewById(R.id.resultImage);
        resultNumberView = findViewById(R.id.result_number);

        // recording
        this.recordController = new RecordController(this);

        recordVoiceView = findViewById(R.id.micro);
        recordVoiceView.setColorFilter(R.color.inactive_microphone);
        recordVoiceView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                    onMicroClicked();
                }
            }
        });
        resultDefectView = findViewById(R.id.resultDefect);

        //
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == UCrop.REQUEST_CROP) {
                if (data != null) {
                    Uri croppedUri = UCrop.getOutput(data);
                    try {
                        toRecognize = Media.getBitmap(this.getContentResolver(), croppedUri);
                        resultImageView.setImageBitmap(toRecognize);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    assert croppedUri != null;
                    uploadFile("http://" + idCompView.getText() + ":8080/upload",
                            new File(croppedUri.getPath()), "Number.png", "image/png");
                }
            }
            else if (requestCode == REQUEST_CODE_PICK_IMAGE) {
                if (data != null) {
                    Uri sourceUri = data.getData();
                    File file = null;
                    try {
                        file = createImageFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Uri destinationUri = Uri.fromFile(file);
                    openCropActivity(sourceUri, destinationUri);
                }
            }
            else if (requestCode == REQUEST_CODE_MAKE_IMAGE) {
                Uri uri = Uri.parse(currentPhotoPath);
                openCropActivity(uri, uri);
            }
        }
    }

    private void openCropActivity(Uri sourceUri, Uri destinationUri) {
        UCrop.Options options = new UCrop.Options();
        options.setMaxScaleMultiplier(30);
        // set colors
        options.setStatusBarColor(ContextCompat.getColor(this, R.color.rjd_theme));
        options.setStatusBarColor(ContextCompat.getColor(this, R.color.rjd_theme));
        //
        UCrop.of(sourceUri, destinationUri)
                .withOptions(options)
                .withMaxResultSize(900, 300)
                .withAspectRatio(3f, 1f)
                .start(MainActivity.this);
    }

    private void openImagesDocument() {
        Intent pictureIntent = new Intent(Intent.ACTION_PICK, Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(Intent.createChooser(pictureIntent,"Select Picture"), REQUEST_CODE_PICK_IMAGE);
    }

    // make photo

    private void photo() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Uri uri = null;
        // authority is set by hands, because with applicationId
        // it gets crop lib appId
        try {
            uri = FileProvider.getUriForFile(this,
                     "app.rjd.provider",
                    createImageFile());
        } catch (IOException e) {
            e.printStackTrace();
        }

        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        startActivityForResult(takePictureIntent, REQUEST_CODE_MAKE_IMAGE);
    }

    @SuppressLint({"SimpleDateFormat"})
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp;
        File storageDir = new File(
                Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DCIM
                ), "Camera");

        File file = File.createTempFile(
                imageFileName,  /* prefix */
                ".png",         /* suffix */
                storageDir      /* directory */
        );

        currentPhotoPath = "file:" + file.getAbsolutePath();
        return file;
    }

    // record voice

    private void enableNoiseSuppressor() {
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor noiseSuppressor = NoiseSuppressor.create(audioSessionId);
            noiseSuppressor.setEnabled(true);
            Log.i("MainMICRO", "NoiseSuppressor enabled");
        } else {
            Log.e("MainMICRO", "This device don't support NoiseSuppressor");
        }
    }

    private CountDownTimer countDownTimer;
    private final double MAX_RECORD_AMPLITUDE = 32768.0D;
    private final long VOLUME_UPDATE_DURATION = 100L;
    private final OvershootInterpolator interpolator = new OvershootInterpolator();
    private int audioSessionId;

    private void onMicroClicked() {
        if (recordController.isAudioRecording()) {
            recordController.stop();
            countDownTimer.cancel();
            this.countDownTimer = null;

            recordVoiceView.setColorFilter(R.color.inactive_microphone);
            handleVolume(0);

            // play();
            // TODO
            uploadFile("http://" + idCompView.getText() + ":8080/upload",
                    recordController.getMyRecordedFile(), "Voice.m4a", "audio/wav");
        } else {
            AudioManager audioManager = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            audioSessionId = audioManager.generateAudioSessionId();
            enableNoiseSuppressor();

            recordVoiceView.clearColorFilter();
            recordController.start();

            countDownTimer = new CountDownTimer(60000L, this.VOLUME_UPDATE_DURATION) {
                public void onTick(long p0) {
                    handleVolume(recordController.getVolume());
                }

                public void onFinish() {
                }
            };
            countDownTimer.start();
        }
    }

    private void handleVolume(int volume) {
        float scale = (float)Math.min(8.0D, (double)volume / this.MAX_RECORD_AMPLITUDE + 1.0D);
        recordVoiceView.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setInterpolator(interpolator)
                .setDuration(this.VOLUME_UPDATE_DURATION);
    }

    private void play() {
        MediaPlayer mp = new MediaPlayer();

        try {
            mp.setDataSource(recordController.getMyRecordedFile().getAbsolutePath());
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}