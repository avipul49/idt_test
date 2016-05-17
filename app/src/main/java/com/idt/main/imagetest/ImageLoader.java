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
public class ImageLoader extends Service implements ImageUtil.OnBitmapSaveListener {
    public static final String DOWNLOAD_COMPLETE = "Download_complete";
    public static final String DOWNLOAD_PROGRESS = "Download_progress";
    public static final String DOWNLOAD_ERROR = "Download_error";

    private LruCache<String, Bitmap> cache;

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
            if (url != null)
                download(url);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public void download(final String imageUrl) {
        Bitmap imageData = cache.get(imageUrl);
        if (imageData != null) {
            ImageUtil.writeToDisk(imageData, ImageLoader.this, Bitmap.CompressFormat.JPEG);
            return;
        }
        new AsyncTask<Void, Integer, Bitmap>() {
            DownloadError error;

            @Override
            protected void onPreExecute() {
            }

            @Override
            protected void onCancelled() {
                Intent intent = new Intent();
                intent.setAction(DOWNLOAD_ERROR);
                intent.putExtra("error", error.getMessage());
                sendBroadcast(intent);
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
                        error = new DownloadError("Invalid content length. The URL is probably not pointing to a file")
                                .setErrorCode(DownloadError.ERROR_INVALID_FILE);
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
                        error = new DownloadError(e).setErrorCode(DownloadError.ERROR_GENERAL_EXCEPTION);
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
                if (result == null) {
                    Intent intent = new Intent();
                    intent.setAction(DOWNLOAD_ERROR);
                    intent.putExtra("error", "Downloaded file could not be decoded as bitmap");
                    sendBroadcast(intent);
                } else {
                    cache.put(imageUrl, result);
                    ImageUtil.writeToDisk(result, ImageLoader.this, Bitmap.CompressFormat.JPEG);
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
        Intent intent = new Intent();
        intent.setAction(DOWNLOAD_ERROR);
        intent.putExtra("error", error.getMessage());
        sendBroadcast(intent);
    }

    public static final class DownloadError extends Throwable {

        private int errorCode;
        public static final int ERROR_GENERAL_EXCEPTION = -1;
        public static final int ERROR_INVALID_FILE = 0;

        public DownloadError(String message) {
            super(message);
        }

        public DownloadError(Throwable error) {
            super(error.getMessage(), error.getCause());
            this.setStackTrace(error.getStackTrace());
        }

        public DownloadError setErrorCode(int code) {
            this.errorCode = code;
            return this;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }

}
