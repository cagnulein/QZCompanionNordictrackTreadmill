package org.cagnulein.qzcompanionnordictracktreadmill;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import com.baidu.paddle.fastdeploy.LitePowerMode;
import com.equationl.fastdeployocr.OCR;
import com.equationl.fastdeployocr.OcrConfig;
import com.equationl.fastdeployocr.RunPrecision;
import com.equationl.fastdeployocr.RunType;
import com.equationl.fastdeployocr.bean.OcrResult;
import com.equationl.fastdeployocr.callback.OcrInitCallback;
import com.equationl.fastdeployocr.callback.OcrRunCallback;

import android.graphics.Rect;
import android.graphics.Point;

import androidx.core.util.Pair;
import android.util.Log;
import android.os.Build;

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";
    private static final String RESULT_CODE = "RESULT_CODE";
    private static final String DATA = "DATA";
    private static final String ACTION = "ACTION";
    private static final String START = "START";
    private static final String STOP = "STOP";
    private static final String SCREENCAP_NAME = "screencap";

    private static int IMAGES_PRODUCED;

    private static final String EXTRA_FOREGROUND_SERVICE_TYPE = "FOREGROUND_SERVICE_TYPE";
    private static final int FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE = 0x20;

    private MediaProjection mMediaProjection;
    private String mStoreDir;
    private ImageReader mImageReader;
    private Handler mHandler;
    private Display mDisplay;
    private VirtualDisplay mVirtualDisplay;
    private int mDensity;
    private int mWidth;
    private int mHeight;
	 private static int mWidthImage;
	 private static int mHeightImage;
    private int mRotation;
    private OrientationChangeCallback mOrientationChangeCallback;

    private OCR ocr;

	 private static String lastText = "";
	 private static String lastTextExtended = "";
	 private static boolean isRunning = false;

	 public static String getLastText() {
		 return lastText;
	 }

    public static String getLastTextExtended() {
		 return lastTextExtended;
	 }

    public static int getImageWidth() {
		 return mWidthImage;
		}

	 public static int getImageHeight() {
		 return mHeightImage;
	 }

    public static Intent getStartIntent(Context context, int resultCode, Intent data) {
        Intent intent = new Intent(context, ScreenCaptureService.class);
        intent.putExtra(ACTION, START);
        intent.putExtra(RESULT_CODE, resultCode);
        intent.putExtra(DATA, data);
        return intent;
    }

    public static Intent getStopIntent(Context context) {
        Intent intent = new Intent(context, ScreenCaptureService.class);
        intent.putExtra(ACTION, STOP);
        return intent;
    }

    private static boolean isStartCommand(Intent intent) {
        return intent.hasExtra(RESULT_CODE) && intent.hasExtra(DATA)
                && intent.hasExtra(ACTION) && Objects.equals(intent.getStringExtra(ACTION), START);
    }

    private static boolean isStopCommand(Intent intent) {
        return intent.hasExtra(ACTION) && Objects.equals(intent.getStringExtra(ACTION), STOP);
    }

    private static int getVirtualDisplayFlags() {
        return DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    }

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            try (Image image = mImageReader.acquireLatestImage()) {
                if (image != null && !isRunning) {
                    Log.i("OCR","running");
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * mWidth;

                    isRunning = true;

                    // Create full bitmap
                    int fullWidth = mWidth + rowPadding / pixelStride;
                    int fullHeight = mHeight;
                    Bitmap fullBitmap = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888);
                    fullBitmap.copyPixelsFromBuffer(buffer);

                    // Calculate the region of interest (last 100 pixels)
                    //int roiHeight = Math.min(100, fullHeight);
                    int roiHeight = fullHeight;
                    int roiY = fullHeight - roiHeight;

                    // Create a new bitmap for the region of interest
                    //Bitmap roiBitmap = Bitmap.createBitmap(fullBitmap, 0, roiY, fullWidth, roiHeight);
		            Bitmap roiBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, fullWidth, fullHeight);

                    // Recycle the full bitmap as we no longer need it
                    fullBitmap.recycle();

                    // Use roiBitmap for OCR

                        ocr.run(roiBitmap, new OcrRunCallback() {
                            @Override
                            public void onSuccess(OcrResult result) {
                                lastText = result.getSimpleText();
                                Log.d("OCR","processed " + lastText);
                                /*Bitmap imgWithBox = result.getImgWithBox();
                                long inferenceTime = (long) result.getInferenceTime();
                                List<OcrResultModel> outputRawResult = result.getOutputRawResult();*/
                                roiBitmap.recycle();
                                isRunning = false;
                            }

                            @Override
                            public void onFail(Throwable e) {
                                Log.e(TAG, "onFail！", e);
                                isRunning = false;
                            }
                        });
                }
            } catch (Exception e) {
                isRunning = false;
                e.printStackTrace();
                isRunning = false;
            }
        }
    }

    private class OrientationChangeCallback extends OrientationEventListener {

        OrientationChangeCallback(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            final int rotation = mDisplay.getRotation();
            if (rotation != mRotation) {
                mRotation = rotation;
                try {
                    // clean up
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);

                    // re-create virtual display depending on device width / height
                    createVirtualDisplay();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.e(TAG, "stopping projection.");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);
                    if (mOrientationChangeCallback != null) mOrientationChangeCallback.disable();
                    mMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
                }
            });
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // create store dir
        File externalFilesDir = getExternalFilesDir(null);
        if (externalFilesDir != null) {
            mStoreDir = externalFilesDir.getAbsolutePath() + "/screenshots/";
            File storeDirectory = new File(mStoreDir);
            if (!storeDirectory.exists()) {
                boolean success = storeDirectory.mkdirs();
                if (!success) {
                    Log.e(TAG, "failed to create file storage directory.");
                    stopSelf();
                }
            }
        } else {
            Log.e(TAG, "failed to create file storage directory, getExternalFilesDir is null.");
            stopSelf();
        }

        ocr = new OCR(getApplicationContext());

        OcrConfig config = new OcrConfig();
// Use the proper Kotlin property access pattern for Java
// Instead of direct field access, use the setter methods generated by Kotlin

// Set model path to assets/models/ch_PP-OCRv2
        config.setModelPath("models/ch_PP-OCRv2"); // Models in assets directory

// Set model file names
        config.setClsModelFileName("cls");
        config.setDetModelFileName("det");
        config.setRecModelFileName("rec");

// Set run type to run all OCR steps
        config.setRunType(RunType.All);

// Use full CPU power for better performance
        config.setCpuPowerMode(LitePowerMode.LITE_POWER_HIGH); // Note: LITE_POWER_FULL seems to be missing from enum, using HIGH

// Set precision mode for all models to FP16
        config.setRecRunPrecision(RunPrecision.LiteFp16);
        config.setDetRunPrecision(RunPrecision.LiteFp16);
        config.setClsRunPrecision(RunPrecision.LiteFp16);

        ocr.initModel(config, new OcrInitCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "init onSuccess");
            }
                @Override
                public void onFail(Throwable e) {
                    Log.e(TAG, "onFail", e);
                }
            });

        // start capture handling thread
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mHandler = new Handler();
                Looper.loop();
            }
        }.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isStartCommand(intent)) {
            // create notification
            Pair<Integer, Notification> notification = NotificationUtils.getNotification(this);

            try {
                int serviceType = intent.getIntExtra(EXTRA_FOREGROUND_SERVICE_TYPE, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(notification.first, notification.second, serviceType);
                } else {
                    startForeground(notification.first, notification.second);
                }
            } catch (Exception e) {
                Log.e("ForegroundService", "Failed to start foreground service", e);
                return START_NOT_STICKY;
            }
            // start projection
            int resultCode = intent.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra(DATA);
            startProjection(resultCode, data);
        } else if (isStopCommand(intent)) {
            stopProjection();
            stopSelf();
        } else {
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void startProjection(int resultCode, Intent data) {
        MediaProjectionManager mpManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mMediaProjection == null) {
            mMediaProjection = mpManager.getMediaProjection(resultCode, data);
            if (mMediaProjection != null) {
                // display metrics
                mDensity = Resources.getSystem().getDisplayMetrics().densityDpi;
                WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                mDisplay = windowManager.getDefaultDisplay();

                // register orientation change callback
                mOrientationChangeCallback = new OrientationChangeCallback(this);
                if (mOrientationChangeCallback.canDetectOrientation()) {
                    mOrientationChangeCallback.enable();
                }

                // register media projection stop callback
                mMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);

                // create virtual display depending on device width / height
                createVirtualDisplay();
            }
        }
    }

    private void stopProjection() {
        if (mHandler != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mMediaProjection != null) {
                        mMediaProjection.stop();
                    }
                }
            });
        }
    }

    @SuppressLint("WrongConstant")
    private void createVirtualDisplay() {
        // get width and height
        mWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        mHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

        // start capture reader
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(SCREENCAP_NAME, mWidth, mHeight,
                mDensity, getVirtualDisplayFlags(), mImageReader.getSurface(), null, mHandler);
        mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
    }
}
