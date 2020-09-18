package com.example.myapplication;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.ChannelClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CUSTOM_TAG";
    private static final String CHANNEL_MSG = "com.example.android.wearable.datalayer.channelfile";
    private static final String FILE_MSG = "given_message.txt";
    private static final String FILE_IMAGE = "taken_image.png";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int TEXT_TEST = 0;

    private ImageView ivImage;
    private Bitmap imageBitmap;
    private Button btnTakePhoto;
    private boolean cameraSupported;
    private ThreadPoolExecutor executorService;

    /*
    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivImage = findViewById(R.id.imageView);
        btnTakePhoto = findViewById(R.id.btnImage);

        cameraSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
        /*
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    MainActivity.this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
         */
        executorService = new ThreadPoolExecutor(4, 5, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());
    }

    @Override
    protected void onResume() {
        super.onResume();

        btnTakePhoto.setEnabled(cameraSupported);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        executorService.shutdown();
    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<>();
        Task<List<Node>> nodeListTask = Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();

        try {
            // Block on a task and get the result synchronously (because this is on a background
            // thread).
            List<Node> nodes = Tasks.await(nodeListTask);

            for (Node node : nodes) {
                results.add(node.getId());
            }

        } catch (ExecutionException exception) {
            Log.e(TAG, "Task failed: " + exception);

        } catch (InterruptedException exception) {
            Log.e(TAG, "Interrupt occurred: " + exception);
        }

        return results;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            imageBitmap = (Bitmap) extras.get("data");
            ivImage.setImageBitmap(imageBitmap);
        }
    }

    /** As simple wrapper around Log.d */
    private static void LOGD(final String tag, String message) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }

    public void onPhotoTake(View view) {
        /**
         * Dispatches an {@link android.content.Intent} to take a photo. Result will be returned back in
         * onActivityResult().
         */
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    public void onSend(View view) {

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Collection<String> nodes = getNodes();
                LOGD(TAG, "Nodes: " + nodes.size());
                for (String node : nodes) {
                    Task<ChannelClient.Channel> channelTask = Wearable.getChannelClient(getApplicationContext()).openChannel(node, CHANNEL_MSG);
                    channelTask.addOnSuccessListener(new OnSuccessListener<ChannelClient.Channel>() {
                        @Override
                        public void onSuccess(ChannelClient.Channel channel) {
                            LOGD(TAG, "onSuccess " + channel.getNodeId());
                            Uri fileUri = null;
                            File outFile = null;
                            //File textFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/given_message.txt");
                            try {
                                if (TEXT_TEST == 1) {
                                    outFile = new File((getApplicationContext().getFileStreamPath(FILE_MSG).getPath()));
                                } else {
                                    outFile = new File((getApplicationContext().getFileStreamPath(FILE_IMAGE).getPath()));
                                }
                                fileUri = Uri.fromFile(outFile);
                                Files.deleteIfExists(Paths.get(fileUri.getPath()));
                                FileOutputStream out = new FileOutputStream(fileUri.getPath());

                                if (TEXT_TEST == 1) {
                                    /** text file stream */
                                    out.write("Hello, wear!".getBytes());
                                } else {
                                    /** image file stream */
                                    if (imageBitmap != null)
                                        imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // PNG is a lossless format, the compression factor (100) is ignored
                                }
                                out.flush();
                                out.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            Wearable.getChannelClient(getApplicationContext()).sendFile(channel, fileUri);
                        }
                    });
                }
            }
        });
    }
}
