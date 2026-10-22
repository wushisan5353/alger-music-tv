package com.wushisan.algertv.ui.phone;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wushisan.algertv.R;
import com.wushisan.algertv.data.MusicRepository;

/** 简版设置：仅自定义 API 地址（与电视版同一套 MusicRepository 偏好）。 */
public class PhoneSettingsActivity extends AppCompatActivity {

    private EditText input;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_settings);

        input = findViewById(R.id.settings_input);
        status = findViewById(R.id.settings_status);
        refreshStatus();

        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicRepository.setCustomApiBase(PhoneSettingsActivity.this,
                        input.getText().toString());
                Toast.makeText(PhoneSettingsActivity.this,
                        R.string.saved, Toast.LENGTH_SHORT).show();
                refreshStatus();
            }
        });
        findViewById(R.id.btn_clear_api).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicRepository.setCustomApiBase(PhoneSettingsActivity.this, null);
                Toast.makeText(PhoneSettingsActivity.this,
                        R.string.saved, Toast.LENGTH_SHORT).show();
                refreshStatus();
            }
        });
    }

    private void refreshStatus() {
        String current = MusicRepository.getCustomApiBase(this);
        status.setText(current == null
                ? getString(R.string.custom_api_off)
                : getString(R.string.custom_api_on) + "：" + current);
        input.setText(current == null ? "" : current);
    }
}
