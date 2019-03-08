package com.movesense.mds.fyssabailu.bailu_app;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.movesense.mds.fyssabailu.MainActivity;
import com.movesense.mds.fyssabailu.R;
import com.movesense.mds.fyssabailu.scanner.MainScanActivity;

import butterknife.BindView;
import butterknife.ButterKnife;

public class FyssaInfoActivity extends AppCompatActivity {

    @BindView(R.id.fyssa_info_nameET) EditText nameText;
    @BindView(R.id.fyssa_info_doneBT) Button doneButton;

    private FyssaApp app;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fyssa_info);
        ButterKnife.bind(this);

        app = (FyssaApp) getApplication();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_info);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_disclaimer)
                .setMessage(R.string.disclaimer)
                .setCancelable(false)
                .setPositiveButton(R.string.accept, (dialog, which) -> {
                    setupListeners();
                })
                .setNegativeButton(R.string.decline, (dialog, which) -> {
                    startActivity(new Intent(FyssaInfoActivity.this, MainActivity.class)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
                })
                .create().show();
    }


    private void setupListeners() {
        doneButton.setOnClickListener(view -> {
            if (nameText.getText().length() == 0) {

            } else {
                if (nameText.getText().length()> 20) {
                    app.getMemoryTools().saveName(nameText.getText().toString().substring(0,19));
                } else {
                    app.getMemoryTools().saveName(nameText.getText().toString());
                }

                startMainActivity();
            }
        });
    }

    private void startMainActivity() {
        startActivity(new Intent(FyssaInfoActivity.this, MainScanActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(FyssaInfoActivity.this, MainScanActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
}
