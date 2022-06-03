package org.cagnulein.qzcompanionnordictracktreadmill;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;

import java.util.logging.Logger;

import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // use this to start and trigger a service
        Intent i= new Intent(getApplicationContext(), QZService.class);
        // potentially add data to the intent
        i.putExtra("KEY1", "Value to be used by the service");
        getApplicationContext().startService(i);

    }
}