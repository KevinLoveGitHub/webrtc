package cn.com.shine.shine_webrtc;

import android.Manifest;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.webrtc.SurfaceViewRenderer;

import lib.webrtc.RtcUtils;

public class MainActivity extends AppCompatActivity {

    private SurfaceViewRenderer mSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission();

        mSurfaceView = (SurfaceViewRenderer) findViewById(R.id.surfaceView);


        findViewById(R.id.join).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Editable editableIP = ((EditText) findViewById(R.id.service_ip)).getText();
                Editable editableID = ((EditText) findViewById(R.id.room_id)).getText();
                String serviceIP = "172.168.1.9";
                String roomID = "123";
                if (editableID != null) {
                    serviceIP = editableIP.toString();
                    roomID = editableID.toString();
                }
                join(serviceIP, roomID);
            }
        });

        findViewById(R.id.finish).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RtcUtils.INSTANCE.destroy();
            }
        });
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.MODIFY_AUDIO_SETTINGS}, 1);
        }
    }

    private void join(String serviceIP, final String roomID) {
        RtcUtils.INSTANCE.init((Application) this.getApplicationContext());
        serviceIP = "https://" + serviceIP;
        RtcUtils.INSTANCE.startRTC(serviceIP, roomID, mSurfaceView, new RtcUtils.SendOfferCallback() {
            @Override
            public void onSend() {
                Toast.makeText(MainActivity.this, roomID, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
