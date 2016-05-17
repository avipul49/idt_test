package com.idt.main.imagetest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;


public class MainActivity extends AppCompatActivity implements ImageUtil.OnImageReadListener, TextView.OnEditorActionListener {

    private ImageView imageView;
    private AutoCompleteTextView imageUrlView;
    private ProgressBar progressBar;
    private ArrayAdapter<String> urlAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageUrlView = (AutoCompleteTextView) findViewById(R.id.image_url);
        imageView = (ImageView) findViewById(R.id.image);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        Set<String> set = getHistory(this);
        urlAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(set));
        imageUrlView.setAdapter(urlAdapter);
        imageUrlView.setImeActionLabel("Download", KeyEvent.KEYCODE_ENTER);
        setTitle("Load image");
        findViewById(R.id.go).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!imageUrlView.getText().toString().equals("")) {
                    loadImage(imageUrlView.getText().toString());
                } else {
                    Toast.makeText(MainActivity.this, "Please enter a URL", Toast.LENGTH_SHORT).show();
                }
            }
        });
        imageUrlView.setOnEditorActionListener(this);
    }

    private void loadImage(String url) {
        urlAdapter.insert(url, 0);
        addHistory(this, url);
        imageUrlView.dismissDropDown();
        progressBar.setVisibility(View.VISIBLE);
        Intent intent = new Intent(this, ImageLoaderService.class);
        intent.putExtra("url", url);
        startService(intent);
        dismissKeyBoard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ImageLoaderService.DOWNLOAD_COMPLETE);
        filter.addAction(ImageLoaderService.DOWNLOAD_PROGRESS);
        filter.addAction(ImageLoaderService.DOWNLOAD_ERROR);
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(receiver);
        super.onPause();
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ImageLoaderService.DOWNLOAD_COMPLETE:
                    String imageUrl = intent.getStringExtra("imageData");
                    progressBar.setVisibility(View.INVISIBLE);
                    ImageUtil.readFromDiskAsync(imageUrl, MainActivity.this);
                    break;
                case ImageLoaderService.DOWNLOAD_PROGRESS:
                    progressBar.setProgress(intent.getIntExtra("progress", 0));
                    break;
                case ImageLoaderService.DOWNLOAD_ERROR:
                    progressBar.setVisibility(View.INVISIBLE);
                    Toast.makeText(MainActivity.this, intent.getStringExtra("error"), Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void dismissKeyBoard() {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(imageUrlView.getWindowToken(), 0);
    }

    @Override
    public void onImageRead(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
    }

    @Override
    public void onReadFailed() {

    }

    public static void addHistory(Context context, String name) {
        SharedPreferences prefs = context.getSharedPreferences("com.idt.main", context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        HashSet<String> set = new HashSet<String>();
        Set<String> history = prefs.getStringSet("history", new HashSet<String>());
        set.add(name);
        set.addAll(history);
        editor.putStringSet("history", set);
        editor.commit();
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, ImageLoaderService.class));
        super.onDestroy();
    }

    public static Set<String> getHistory(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("com.idt.main", context.MODE_PRIVATE);
        return prefs.getStringSet("history", new HashSet<String>());
    }

    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_NULL
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (!imageUrlView.getText().toString().equals("")) {
                loadImage(imageUrlView.getText().toString());
                return true;
            } else {
                Toast.makeText(MainActivity.this, "Please enter a URL", Toast.LENGTH_SHORT).show();
            }
        }
        return false;
    }
}
