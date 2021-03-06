package com.idt.main.imagetest;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.LruCache;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by vipulmittal on 17/05/16.
 */
public class ImageLoaderService extends Service implements ImageUtil.OnBitmapSaveListener {
    public static final String DOWNLOAD_COMPLETE = "Download_complete";
    public static final String DOWNLOAD_PROGRESS = "Download_progress";
    public static final String DOWNLOAD_ERROR = "Download_error";

    private LruCache<String, Bitmap> cache;
    private boolean inProgress;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (cache == null) {
                final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

                final int cacheSize = maxMemory / 8;

                cache = new LruCache<String, Bitmap>(cacheSize) {
                    @Override
                    protected int sizeOf(String key, Bitmap bitmap) {
                        return bitmap.getByteCount() / 1024;
                    }
                };
            }
            String url = intent.getStringExtra("url");
            if (inProgress) {
                sendError("Download in progress.", false);
            } else if (url != null) {
                download(url);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public void download(final String imageUrl) {
        Bitmap imageData = cache.get(imageUrl);
        if (imageData != null) {
            ImageUtil.writeToDisk(imageData, ImageLoaderService.this, Bitmap.CompressFormat.JPEG);
            return;
        }
        new AsyncTask<Void, Integer, Bitmap>() {
            String error;

            @Override
            protected void onPreExecute() {
                inProgress = true;
            }

            @Override
            protected void onCancelled() {
                inProgress = false;
                sendError(error, true);
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                Intent intent = new Intent();
                intent.setAction(DOWNLOAD_PROGRESS);
                intent.putExtra("progress", values[0]);
                sendBroadcast(intent);
            }

            @Override
            protected Bitmap doInBackground(Void... params) {
                Bitmap bitmap = null;
                HttpURLConnection connection = null;
                InputStream is = null;
                ByteArrayOutputStream out = null;
                try {
                    connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                    connection.connect();
                    final int length = connection.getContentLength();
                    if (length <= 0) {
                        error = "Invalid content length. The URL is probably not pointing to a file";
                        this.cancel(true);
                    }
                    is = new BufferedInputStream(connection.getInputStream(), 8192);
                    out = new ByteArrayOutputStream();
                    byte bytes[] = new byte[8192];
                    int count;
                    long read = 0;
                    while ((count = is.read(bytes)) != -1) {
                        read += count;
                        out.write(bytes, 0, count);
                        publishProgress((int) ((read * 100) / length));
                    }
                    bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());
                } catch (Throwable e) {
                    if (!this.isCancelled()) {
                        error = e.getMessage();
                        this.cancel(true);
                    }
                } finally {
                    try {
                        if (connection != null)
                            connection.disconnect();
                        if (out != null) {
                            out.flush();
                            out.close();
                        }
                        if (is != null)
                            is.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap result) {
                inProgress = false;
                if (result == null) {
                    sendError("Downloaded file could not be decoded as bitmap", true);
                } else {
                    cache.put(imageUrl, result);
                    ImageUtil.writeToDisk(result, ImageLoaderService.this, Bitmap.CompressFormat.JPEG);
                }
            }
        }.execute();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onBitmapSaved(String uri) {
        Intent intent = new Intent();
        intent.setAction(DOWNLOAD_COMPLETE);
        intent.putExtra("imageData", uri);
        sendBroadcast(intent);
    }

    @Override
    public void onBitmapSaveError(ImageUtil.DiscError error) {
        sendError(error.getMessage(), true);
    }

    private void sendError(String msg, boolean dismissProgressBar) {
        Intent intent = new Intent();
        intent.setAction(DOWNLOAD_ERROR);
        intent.putExtra("error", msg);
        intent.putExtra("dismissProgressBar", dismissProgressBar);
        sendBroadcast(intent);
    }
}
