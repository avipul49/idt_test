package com.idt.main.imagetest;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Created by vipulmittal on 17/05/16.
 */
public class ImageUtil {

    public static void writeToDisk(final Bitmap image,
                                   final OnBitmapSaveListener listener,
                                   final Bitmap.CompressFormat format) {
        String path = Environment.getExternalStorageDirectory().toString();
        File dir = new File(path, "idt");
        if (!dir.exists()) {
            dir.mkdir();
        }
        final File imageFile = new File(path + "/idt", "idt_image" + new Random().nextInt() + ".jpg");

        if (imageFile.exists()) {
            if (!imageFile.delete()) {
                listener.onBitmapSaveError(new DiscError("could not delete existing file, " +
                        "most likely the write permission was denied")
                        .setErrorCode(DiscError.ERROR_PERMISSION_DENIED));
                return;
            }
        }

        try {
            if (!imageFile.createNewFile()) {
                listener.onBitmapSaveError(new DiscError("could not create file")
                        .setErrorCode(DiscError.ERROR_PERMISSION_DENIED));
                return;
            }
        } catch (IOException e) {
            listener.onBitmapSaveError(new DiscError(e).setErrorCode(DiscError.ERROR_GENERAL_EXCEPTION));
            return;
        }

        new AsyncTask<Void, Void, Void>() {

            private DiscError error;

            @Override
            protected Void doInBackground(Void... params) {
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(imageFile);
                    rotateImage(image, 180).compress(format, 100, fos);
                } catch (IOException e) {
                    error = new DiscError(e).setErrorCode(DiscError.ERROR_GENERAL_EXCEPTION);
                    this.cancel(true);
                } finally {
                    if (fos != null) {
                        try {
                            fos.flush();
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                return null;
            }

            @Override
            protected void onCancelled() {
                listener.onBitmapSaveError(error);
            }

            @Override
            protected void onPostExecute(Void result) {
                listener.onBitmapSaved(imageFile.getAbsolutePath());
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public interface OnBitmapSaveListener {
        void onBitmapSaved(String uri);

        void onBitmapSaveError(DiscError error);
    }

    public static final class DiscError extends Throwable {

        private int errorCode;
        public static final int ERROR_GENERAL_EXCEPTION = -1;
        public static final int ERROR_PERMISSION_DENIED = 3;

        public DiscError(String message) {
            super(message);
        }

        public DiscError(Throwable error) {
            super(error.getMessage(), error.getCause());
            this.setStackTrace(error.getStackTrace());
        }

        public DiscError setErrorCode(int code) {
            this.errorCode = code;
            return this;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }

    public static void readFromDiskAsync(String imagePath, final OnImageReadListener listener) {
        new AsyncTask<String, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(String... params) {
                return BitmapFactory.decodeFile(params[0]);
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null)
                    listener.onImageRead(bitmap);
                else
                    listener.onReadFailed();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, imagePath);
    }

    public interface OnImageReadListener {
        void onImageRead(Bitmap bitmap);

        void onReadFailed();
    }

    public static Bitmap rotateImage(Bitmap src, float degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap bmp = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
        return bmp;
    }
}
