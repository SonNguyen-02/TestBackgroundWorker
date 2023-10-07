package com.mct.worker;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.mct.worker.worker.ProcessWorker;

import java.util.UUID;

public class ProcessActivity extends AppCompatActivity {

    private static final String UUID_KEY = "key_uuid";

    final String[] times = {"1s", "3s", "5s", "10s", "20s", "30s", "40s", "50s", "60s"};

    EditText edtA;
    EditText edtB;
    AppCompatSpinner spnTime;
    Button btnProcess;
    TextView tvResult;
    ViewGroup llProgress;
    TextView tvProcessing;
    Button btnCancel;
    Button btnFinish;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.process_activity);

        initView();
        initData();

        registerObserver();
    }

    private void initView() {
        edtA = findViewById(R.id.edt_a);
        edtB = findViewById(R.id.edt_b);
        spnTime = findViewById(R.id.spn_time);
        btnProcess = findViewById(R.id.btn_process);
        tvResult = findViewById(R.id.tv_result);
        llProgress = findViewById(R.id.ll_progress);
        tvProcessing = findViewById(R.id.tv_processing);
        btnCancel = findViewById(R.id.btn_cancel);
        btnFinish = findViewById(R.id.btn_finish);
    }

    private void initData() {

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, times);
        spnTime.setAdapter(adapter);

        btnProcess.setOnClickListener(v -> {
            try {
                hideKeyboard(getCurrentFocus());

                int a = Integer.parseInt(edtA.getText().toString());
                int b = Integer.parseInt(edtB.getText().toString());
                String timeString = spnTime.getSelectedItem().toString();
                int time = Integer.parseInt(timeString.substring(0, timeString.length() - 1));

                OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(ProcessWorker.class)
                        .setInputData(ProcessWorker.newInputData(a, b, time * 1000L))
                        .build();
                saveId(workRequest.getId());
                WorkManager.getInstance(this).enqueue(workRequest);

                registerObserver();

            } catch (Throwable t) {
                Toast.makeText(this, t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        btnCancel.setOnClickListener(v -> {
            if (getId() == null) {
                return;
            }
            WorkManager.getInstance(this).cancelWorkById(getId());
        });
        btnFinish.setOnClickListener(v -> finish());
    }

    private void registerObserver() {
        if (getId() == null) {
            return;
        }
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(getId())
                .observe(this, workInfo -> {
                    if (workInfo.getState() == WorkInfo.State.ENQUEUED) {
                        updateResult(null);
                    }
                    if (workInfo.getState() == WorkInfo.State.RUNNING) {
                        showProcessing(true, "Processing... " + workInfo.getProgress().getFloat(ProcessWorker.KEY_PROCESS_PERCENT, 0) + "%");
                    }
                    if (workInfo.getState().isFinished()) {
                        saveId(null);
                        showProcessing(false, null);
                        updateResult(workInfo.getOutputData().getString(ProcessWorker.KEY_RESULT));
                        if (workInfo.getState() == WorkInfo.State.CANCELLED) {
                            Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void showProcessing(boolean show, String message) {
        if (show) {
            llProgress.setVisibility(View.VISIBLE);
            tvProcessing.setText(message);
        } else {
            llProgress.setVisibility(View.GONE);
        }
    }

    private void updateResult(String result) {
        if (result == null) {
            tvResult.setText("Result: ");
        } else {
            tvResult.setText(result);
        }
    }

    private void saveId(UUID id) {
        if (id == null) {
            getPreferences(0).edit().remove(UUID_KEY).apply();
        } else {
            getPreferences(0).edit().putString(UUID_KEY, id.toString()).apply();
        }
    }

    @Nullable
    private UUID getId() {
        String uuid = getPreferences(0).getString(UUID_KEY, null);
        if (uuid == null) {
            return null;
        } else {
            return UUID.fromString(getPreferences(0).getString(UUID_KEY, null));
        }
    }

    private void hideKeyboard(View view) {
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            view.clearFocus();
        }
    }
}
