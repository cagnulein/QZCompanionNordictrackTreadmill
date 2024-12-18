package org.cagnulein.qzcompanionnordictracktreadmill;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
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
import java.util.Objects;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import android.media.ImageReader.OnImageAvailableListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import android.graphics.Rect;
import android.graphics.Point;

import androidx.annotation.NonNull;
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
    private static final int FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE = 0x10;

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

    private TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

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
        private static final String TAG = "OCR";
        private static final int PROCESSING_TIMEOUT_MS = 5000;
        private final Handler timeoutHandler = new Handler();

        private class BitmapHolder {
            Bitmap fullBitmap;
            Bitmap roiBitmap;

            void recycleBitmaps() {
                if (fullBitmap != null && !fullBitmap.isRecycled()) {
                    fullBitmap.recycle();
                    Log.d(TAG, "Full bitmap recycled");
                }
                if (roiBitmap != null && !roiBitmap.isRecycled()) {
                    roiBitmap.recycle();
                    Log.d(TAG, "ROI bitmap recycled");
                }
            }
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable called");

            // If already processing, acquire and discard the image
            if (isRunning) {
                Log.d(TAG, "System busy - acquiring and discarding latest image");
                Image skippedImage = mImageReader.acquireLatestImage();
                if (skippedImage != null) {
                    skippedImage.close();
                    Log.d(TAG, "Skipped image closed");
                }
                return;
            }

            final BitmapHolder bitmapHolder = new BitmapHolder();
            Image image = null;

            try {
                // Acquire the most recent image
                image = mImageReader.acquireLatestImage();
                if (image == null) {
                    Log.e(TAG, "Acquired image is null");
                    return;
                }

                isRunning = true;
                Log.d(TAG, "Starting image processing");

                // Set timeout
                timeoutHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isRunning) {
                            isRunning = false;
                            bitmapHolder.recycleBitmaps();
                            Log.w(TAG, "Processing timeout reached - forcing reset");
                        }
                    }
                }, PROCESSING_TIMEOUT_MS);

                // Get image data
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * mWidth;

                // Create full bitmap
                int fullWidth = mWidth + rowPadding / pixelStride;
                int fullHeight = mHeight;
                Log.d(TAG, "Creating bitmap with dimensions: " + fullWidth + "x" + fullHeight);

                bitmapHolder.fullBitmap = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888);
                bitmapHolder.fullBitmap.copyPixelsFromBuffer(buffer);

                // Create ROI
                int roiHeight = Math.min(300, fullHeight);
                Log.d(TAG, "Creating ROI bitmap with height: " + roiHeight);

                bitmapHolder.roiBitmap = Bitmap.createBitmap(bitmapHolder.fullBitmap, 0, 0, fullWidth, roiHeight);
                bitmapHolder.fullBitmap.recycle();
                Log.d(TAG, "Full bitmap recycled after ROI creation");

                // Process OCR
                InputImage inputImage = InputImage.fromBitmap(bitmapHolder.roiBitmap, 0);
                Log.d(TAG, "Starting OCR processing");

                recognizer.process(inputImage)
                        .addOnSuccessListener(new OnSuccessListener<Text>() {
                            @Override
                            public void onSuccess(Text result) {
                                Log.d(TAG, "OCR processing successful");
                                try {
                                    String resultText = result.getText();
                                    lastText = resultText;
                                    lastTextExtended = "";

                                    for (Text.TextBlock block : result.getTextBlocks()) {
                                        String blockText = block.getText();
                                        Rect blockFrame = block.getBoundingBox();
                                        if (blockFrame != null) {
                                            blockFrame.offset(0, 0);
                                        }
                                        lastTextExtended += blockText + "$$" + blockFrame.toString() + "§§";
                                    }

                                    Log.d(TAG, "Processed text length: " + resultText.length());
                                    Log.d(TAG, "Number of text blocks: " + result.getTextBlocks().size());
                                } catch (Exception e) {
                                    Log.e(TAG, "Error processing OCR result", e);
                                }
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "OCR processing failed", e);
                            }
                        })
                        .addOnCompleteListener(new OnCompleteListener<Text>() {
                            @Override
                            public void onComplete(@NonNull Task<Text> task) {
                                Log.d(TAG, "OCR task completed");
                                timeoutHandler.removeCallbacksAndMessages(null);
                                isRunning = false;
                                bitmapHolder.recycleBitmaps();
                            }
                        });

            } catch (Exception e) {
                Log.e(TAG, "Error in image processing", e);
                isRunning = false;
                if (bitmapHolder != null) {
                    bitmapHolder.recycleBitmaps();
                }
            } finally {
                if (image != null) {
                    image.close();
                    Log.d(TAG, "Image closed");
                }
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

                // create virtual display depending on device width / height
                createVirtualDisplay();

                // register orientation change callback
                mOrientationChangeCallback = new OrientationChangeCallback(this);
                if (mOrientationChangeCallback.canDetectOrientation()) {
                    mOrientationChangeCallback.enable();
                }

                // register media projection stop callback
                mMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);
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
