package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.wearable.ChannelClient;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends WearableActivity {

    private static final String TAG = "CUSTOM_TAG";
    private static final String FILE_MSG = "given_message.txt";
    private static final String FILE_IMAGE = "taken_image.png";
    private static final int TEXT_TEST = 0;

    private ThreadPoolExecutor executorService;

    private ImageView ivImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivImage = findViewById(R.id.imageView);

        // Enables Always-on
        setAmbientEnabled();
    }

    @Override
    protected void onResume() {
        super.onResume();

        executorService = new ThreadPoolExecutor(4, 5, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

        Wearable.getChannelClient(getApplicationContext()).registerChannelCallback(new ChannelClient.ChannelCallback() {
            @Override
            public void onChannelOpened(@NonNull ChannelClient.Channel channel) {
                super.onChannelOpened(channel);
                Log.d(TAG, "onChannelOpened");

                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        File outFile = null;
                        if (TEXT_TEST == 1) {
                            outFile = new File((getApplicationContext().getFileStreamPath(FILE_MSG).getPath()));
                        } else {
                            outFile = new File((getApplicationContext().getFileStreamPath(FILE_IMAGE).getPath()));
                        }
                        Uri fileUri = Uri.fromFile(outFile);

                        Wearable.getChannelClient(getApplicationContext()).receiveFile(channel, fileUri, false);
                        Wearable.getChannelClient(getApplicationContext()).registerChannelCallback(new ChannelClient.ChannelCallback() {
                            @Override
                            public void onInputClosed(@NonNull final ChannelClient.Channel channel, int i, int i1) {
                                super.onInputClosed(channel, i, i1);
                                Log.d(TAG, "onInputClosed");
                                if (TEXT_TEST == 1) {
                                    try {
                                        String text = "";
                                        int read;
                                        byte[] data = new byte[1024];
                                        InputStream in = new FileInputStream(fileUri.getPath());

                                        while ((read = in.read(data, 0, data.length)) != -1) {
                                            text += new String(data, StandardCharsets.UTF_8);
                                        }

                                        final String finalText = text;
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(getApplicationContext(), finalText, Toast.LENGTH_LONG).show();
                                            }
                                        });
                                        in.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    /**
                                     * image file stream
                                     */
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Bitmap image = BitmapFactory.decodeFile(fileUri.getPath());
                                            if (image != null) {
                                                ivImage.setImageBitmap(image);
                                            }
                                        }
                                    });
                                }
                                Wearable.getChannelClient(getApplicationContext()).close(channel);
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        executorService.shutdown();
    }
}
