package com.mct.worker.worker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.mct.worker.R;

import java.util.Locale;
import java.util.Random;

public class ProcessWorker extends Worker {

    ///////////////////////////////////////////////////////////////////////////
    // area utils
    ///////////////////////////////////////////////////////////////////////////

    public static final String KEY_RESULT = "_result";
    public static final String KEY_PROCESS_PERCENT = "_process_percent";
    public static final String KEY_NUMBER_A = "_num_a";
    public static final String KEY_NUMBER_B = "_num_b";
    public static final String KEY_PROCESS_TIME = "_process_time";

    @NonNull
    public static Data newInputData(int a, int b, @IntRange(from = 0) long processTime) {
        return new Data.Builder()
                .putInt(KEY_NUMBER_A, a)
                .putInt(KEY_NUMBER_B, b)
                .putLong(KEY_PROCESS_TIME, processTime)
                .build();
    }

    @NonNull
    private static Data makeDataProcess(float processPercent) {
        return new Data.Builder()
                .putFloat(KEY_PROCESS_PERCENT, processPercent)
                .build();
    }

    @NonNull
    private static Data makeDataResult(String result) {
        return new Data.Builder()
                .putString(KEY_RESULT, result)
                .build();
    }

    ///////////////////////////////////////////////////////////////////////////
    // area worker
    ///////////////////////////////////////////////////////////////////////////

    private static final String CHANNEL_ID = "process-worker";
    private static final String CHANNEL_NAME = "process-channel";
    private static final int PROGRESS_MAX = 100;
    private static final String TAG = ProcessWorker.class.getSimpleName();

    private long timeStart;
    private int a, b;
    private int result;

    public ProcessWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.e(TAG, "doWork ");
        timeStart = System.currentTimeMillis();
        a = getInputData().getInt(KEY_NUMBER_A, 0);
        b = getInputData().getInt(KEY_NUMBER_B, 0);
        long processTime = getInputData().getLong(KEY_PROCESS_TIME, 0);

        int step = rand(20, 40);
        long stepDelay = processTime / step;

        Log.e(TAG, "doWork: Initialize");
        setForegroundAsync(createForegroundInfo("Initialize", 0));

        for (int i = 0; i < step; i++) {
            if (isStopped()) {
                return endWork(Result.failure(makeDataResult("Stopped!")));
            }
            int percent = (int) ((float) i / step * PROGRESS_MAX);
            Log.e(TAG, "doWork: Processing... " + percent);
            setProgressAsync(makeDataProcess(percent));
            setForegroundAsync(createForegroundInfo("Processing...", percent));
            try {
                Thread.sleep(stepDelay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (isStopped()) {
            return endWork(Result.failure(makeDataResult("Stopped!")));
        }

        result = a + b;
        Log.e(TAG, "doWork: Done");
        return endWork(Result.success(makeDataResult(getSuccessMessage())));
    }

    private Result endWork(@NonNull Result r) {
        if (!isStopped()) {
            if (r instanceof Result.Success) {
                createResultNotification(getSuccessMessage(), true);
            }
            if (r instanceof Result.Failure) {
                createResultNotification("Failure!", false);
            }
        }

        Log.e(TAG, "endWork: " + (System.currentTimeMillis() - timeStart));
        return r;
    }

    @NonNull
    private String getSuccessMessage() {
        return String.format(Locale.ENGLISH, "Result: %d + %d = %d", a, b, result);
    }

    private void createResultNotification(String message, boolean success) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel();
        }
        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        String title = "Process " + (success ? "success" : "fail");
        int notificationId = getId().hashCode() + hashCode();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setAutoCancel(true)
                .setTicker(title)
                .setContentTitle(title)
                .setContentText(message)
                .setWhen(System.currentTimeMillis())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_process_done);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    @NonNull
    private ForegroundInfo createForegroundInfo(@NonNull String message, int currentProgress) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel();
        }
        Context context = getApplicationContext();

        String title = "Converting...";
        PendingIntent intent = WorkManager.getInstance(context).createCancelPendingIntent(getId());

        int notificationId = getId().hashCode();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setWhen(System.currentTimeMillis())
                .setOngoing(true)
                .setTicker(title)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSmallIcon(R.drawable.ic_process)
                .setProgress(PROGRESS_MAX, currentProgress, false)
                .addAction(0, "Cancel", intent);

        return new ForegroundInfo(notificationId, builder.build());
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createChannel() {
        // Create a Notification channel
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = getApplicationContext().getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private static int rand(int from, int to) {
        return new Random().nextInt(to - from + 1) + from;
    }
}
