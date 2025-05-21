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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.baidu.paddle.fastdeploy.LitePowerMode;
import com.equationl.fastdeployocr.OCR;
import com.equationl.fastdeployocr.OcrConfig;
import com.equationl.fastdeployocr.RunPrecision;
import com.equationl.fastdeployocr.RunType;
import com.equationl.fastdeployocr.bean.OcrResult;
import com.equationl.fastdeployocr.bean.OcrResultModel;
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

    private static final String SPEED_LABEL_BASE = "SPEED";
    private static String cachedSpeedLabelText = SPEED_LABEL_BASE;
    private static Rect cachedSpeedLabelBounds = null;
    private static Rect cachedSpeedValueBounds = null;
    private static long cacheTimestamp = 0;
    private static final long CACHE_EXPIRY_MS = 60000;


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

    /**
     * Saves a bitmap image for debugging purposes without any compression or quality loss
     * @param bitmap The bitmap to save
     * @param prefix The prefix for the filename to identify the source/purpose
     */
    private void saveImageForDebug(Bitmap bitmap, String prefix) {
        try {
            // Create directory if it doesn't exist
            File storeDirectory = new File(mStoreDir);
            if (!storeDirectory.exists()) {
                boolean success = storeDirectory.mkdirs();
                if (!success) {
                    Log.e(TAG, "Failed to create debug directory.");
                    return;
                }
            }
            
            // Create file with timestamp to avoid overwriting
            File file = new File(mStoreDir, prefix + "_" + System.currentTimeMillis() + ".png");
            
            // Save as PNG (lossless format) with 100% quality
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            
            Log.d(TAG, "Debug image saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to save debug image", e);
        }
    }

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

        private String processOcrResults(OcrResult result) {
            final String DEBUG_TAG = "OCRDEBUG_PROCESS"; // Specific tag for debug logs in this method
            final String INCLINE_PATTERN = "-?\\d+\\.\\d+"; // Pattern to match valid incline values (e.g., -15.0 to 30.0)

            List<OcrResultModel> outputResults = result.getOutputRawResult();
            StringBuilder processedText = new StringBuilder();

            Log.d(DEBUG_TAG, "Starting OCR processing with " + outputResults.size() + " detected text elements");

            // Create arrays to store detected text and their bounding boxes
            String[] texts = new String[outputResults.size()];
            Rect[] bounds = new Rect[outputResults.size()];

            // First pass: collect all text and their bounds
            for (int i = 0; i < outputResults.size(); i++) {
                OcrResultModel model = outputResults.get(i);
                texts[i] = model.getLabel();

                Log.d(DEBUG_TAG, "Text element " + i + ": " + texts[i] + ", confidence: " + model.getConfidence());

                // Create bounding rectangle from points
                List<Point> points = model.getPoints();
                if (points.size() >= 4) {
                    int left = Integer.MAX_VALUE;
                    int top = Integer.MAX_VALUE;
                    int right = 0;
                    int bottom = 0;

                    for (Point p : points) {
                        left = Math.min(left, p.x);
                        top = Math.min(top, p.y);
                        right = Math.max(right, p.x);
                        bottom = Math.max(bottom, p.y);
                    }

                    bounds[i] = new Rect(left, top, right, bottom);
                    Log.d(DEBUG_TAG, "Bounds for element " + i + ": " + bounds[i].toString());
                }
            }

            // Second pass: categorize text as either a label or a value
            List<Integer> labelIndices = new ArrayList<>();
            List<Integer> valueIndices = new ArrayList<>();
            boolean speedLabelFound = false;
            int speedLabelIndex = -1;

            for (int i = 0; i < texts.length; i++) {
                if (bounds[i] == null || texts[i] == null) continue;

                String text = texts[i].trim().toUpperCase();

                // Identify labels - they typically contain these words
                if (text.contains("INCLINE") || text.contains("SPEED") ||
                        text.contains("DISTANCE") || text.contains("TIME") ||
                        text.contains("CALORIES") || text.contains("LAP")) {
                    labelIndices.add(i);
                    Log.d(DEBUG_TAG, "Identified LABEL: " + text + " at index " + i);

                    // Check if this is the speed label
                    if (text.contains(SPEED_LABEL_BASE)) {
                        speedLabelFound = true;
                        speedLabelIndex = i;
                        // Update our cached speed label bounds and the full text
                        cachedSpeedLabelBounds = bounds[i];
                        cachedSpeedLabelText = text;
                        cacheTimestamp = System.currentTimeMillis();
                        Log.d(DEBUG_TAG, "Updated cached Speed label bounds: " + cachedSpeedLabelBounds);
                        Log.d(DEBUG_TAG, "Updated cached Speed label text: " + cachedSpeedLabelText);
                    }
                }
                // Identify values - they are typically numeric
                else if (text.matches(".*[0-9]+.*")) {
                    valueIndices.add(i);
                    Log.d(DEBUG_TAG, "Identified VALUE: " + text + " at index " + i);
                } else {
                    Log.d(DEBUG_TAG, "Unclassified text: " + text + " at index " + i);
                }
            }

            Log.d(DEBUG_TAG, "Found " + labelIndices.size() + " labels and " + valueIndices.size() + " values");

            // Check if we need to use the cached speed label
            if (!speedLabelFound && cachedSpeedLabelBounds != null) {
                long currentTime = System.currentTimeMillis();
                // Only use cache if it hasn't expired
                if (currentTime - cacheTimestamp < CACHE_EXPIRY_MS) {
                    Log.d(DEBUG_TAG, "Speed label not found in current frame. Using cached bounds.");
                    // Create a dummy index for the cached speed label
                    speedLabelIndex = texts.length; // Use an index beyond the array size to avoid conflicts
                    labelIndices.add(speedLabelIndex); // Add to label indices

                    // We don't have the actual text in the current frame, so use the cached text
                    String[] newTexts = new String[texts.length + 1];
                    System.arraycopy(texts, 0, newTexts, 0, texts.length);
                    newTexts[speedLabelIndex] = cachedSpeedLabelText;
                    texts = newTexts;

                    // Also add the cached bounds
                    Rect[] newBounds = new Rect[bounds.length + 1];
                    System.arraycopy(bounds, 0, newBounds, 0, bounds.length);
                    newBounds[speedLabelIndex] = cachedSpeedLabelBounds;
                    bounds = newBounds;

                    Log.d(DEBUG_TAG, "Added cached Speed label at index " + speedLabelIndex + ": " + cachedSpeedLabelText);
                } else {
                    Log.d(DEBUG_TAG, "Cached speed label has expired. Not using cache.");
                    // Clear the cache after expiry
                    cachedSpeedLabelBounds = null;
                    cachedSpeedValueBounds = null;
                }
            }

            // Third pass: match each label with the closest value
            for (int labelIdx : labelIndices) {
                Rect labelBounds = bounds[labelIdx];
                if (labelBounds == null) continue;

                int closestValueIdx = -1;
                double closestDistance = Double.MAX_VALUE;

                // Check if we have a cached value for the speed label
                boolean isSpeedLabel = (labelIdx == speedLabelIndex);

                if (isSpeedLabel && cachedSpeedValueBounds != null &&
                        System.currentTimeMillis() - cacheTimestamp < CACHE_EXPIRY_MS) {
                    // Try to find if the cached value position still has a value in it
                    for (int valueIdx : valueIndices) {
                        Rect valueBounds = bounds[valueIdx];
                        if (valueBounds != null &&
                                valueBounds.intersect(cachedSpeedValueBounds) &&
                                (float)valueBounds.width() * valueBounds.height() /
                                        (cachedSpeedValueBounds.width() * cachedSpeedValueBounds.height()) > 0.5f) {
                            // Found a value in approximately the same position as our cached speed value
                            closestValueIdx = valueIdx;
                            Log.d(DEBUG_TAG, "Found value at cached position for Speed: " + texts[valueIdx]);
                            break;
                        }
                    }
                }

                // If we couldn't find a value at the cached position, or this isn't the speed label,
                // find the closest value normally
                if (closestValueIdx == -1) {
                    Log.d(DEBUG_TAG, "Finding match for label: " + texts[labelIdx]);

                    for (int valueIdx : valueIndices) {
                        Rect valueBounds = bounds[valueIdx];
                        if (valueBounds == null) continue;

                        // Calculate distance between centers of rectangles
                        double labelCenterX = (labelBounds.left + labelBounds.right) / 2.0;
                        double labelCenterY = (labelBounds.top + labelBounds.bottom) / 2.0;
                        double valueCenterX = (valueBounds.left + valueBounds.right) / 2.0;
                        double valueCenterY = (valueBounds.top + valueBounds.bottom) / 2.0;

                        // For treadmill displays, values are typically above labels
                        // So we prioritize values that are above (smaller Y) and aligned horizontally
                        if (valueCenterY < labelCenterY) { // Value is above the label
                            // Calculate horizontal distance
                            double horizontalDist = Math.abs(labelCenterX - valueCenterX);
                            // Calculate vertical distance
                            double verticalDist = labelCenterY - valueCenterY;

                            // Use a weighted distance that prioritizes horizontal alignment
                            double distance = horizontalDist * 3 + verticalDist;

                            Log.d(DEBUG_TAG, "  Candidate value: " + texts[valueIdx] +
                                    ", horizontalDist: " + horizontalDist +
                                    ", verticalDist: " + verticalDist +
                                    ", weighted distance: " + distance);

                            if (distance < closestDistance) {
                                closestDistance = distance;
                                closestValueIdx = valueIdx;
                                Log.d(DEBUG_TAG, "  New closest value: " + texts[valueIdx] +
                                        " with distance: " + closestDistance);
                            }
                        }
                    }
                }

                // If we found a matching value
                if (closestValueIdx >= 0) {
                    Log.d(DEBUG_TAG, "MATCH FOUND: Label '" + texts[labelIdx] +
                            "' matched with value '" + texts[closestValueIdx] + "'");

                    // If this is the speed label, update our cached value bounds
                    if (isSpeedLabel) {
                        cachedSpeedValueBounds = bounds[closestValueIdx];
                        cacheTimestamp = System.currentTimeMillis();
                        Log.d(DEBUG_TAG, "Updated cached SPEED value bounds: " + cachedSpeedValueBounds);
                    }

                    String valueText = texts[closestValueIdx].trim();
                    String labelText = texts[labelIdx].trim().toUpperCase();

                    // Add specific validation for incline values
                    if (labelText.contains("INCLINE")) {
                        // Check if the value matches our incline pattern (e.g., "5.0", "12.0")
                        if (valueText.matches(INCLINE_PATTERN)) {
                            // Valid incline value format
                            try {
                                double inclineValue = Double.parseDouble(valueText);
                                // Check if the value is in a reasonable range (-15.0 to 30.0)
                                if (inclineValue >= -15.0 && inclineValue <= 30.0) {
                                    // Valid incline value, add it to the processed text
                                    processedText.append(valueText).append("\n")
                                            .append(texts[labelIdx]).append("\n");
                                    Log.d(DEBUG_TAG, "Valid incline value: " + valueText);
                                } else {
                                    Log.d(DEBUG_TAG, "Incline value out of range: " + valueText);
                                }
                            } catch (NumberFormatException e) {
                                Log.d(DEBUG_TAG, "Failed to parse incline value: " + valueText);
                            }
                        } else {
                            Log.d(DEBUG_TAG, "Invalid incline value format: " + valueText +
                                    " - doesn't match pattern: " + INCLINE_PATTERN);
                        }
                    } else {
                        // For all other labels, just add the value and label
                        processedText.append(valueText).append("\n")
                                .append(texts[labelIdx]).append("\n");
                    }
                } else {
                    Log.d(DEBUG_TAG, "No match found for label: " + texts[labelIdx]);
                }
            }

            // Add any remaining important information (like iFIT status, etc.)
            for (int i = 0; i < texts.length; i++) {
                if (texts[i] != null &&
                        (texts[i].contains("iFIT") ||
                                texts[i].contains("Workout") ||
                                texts[i].contains("END") ||
                                texts[i].contains("BEGIN"))) {
                    Log.d(DEBUG_TAG, "Adding special text: " + texts[i]);
                    processedText.append(texts[i]).append("\n");
                }
            }

            Log.d(DEBUG_TAG, "Final processed text:\n" + processedText.toString());
            return processedText.toString();
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            try (Image image = mImageReader.acquireLatestImage()) {
                if (image != null && !isRunning) {
                    Log.i("OCR", "Running OCR processing");
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
                    
                    // Save full screen bitmap for debugging
                    saveImageForDebug(fullBitmap, "full_screen");

                    // Calculate the region of interest
                    int roiHeight = fullHeight;
                    int roiY = fullHeight - roiHeight;

                    // Create a new bitmap for the region of interest (top 20%)
                    Bitmap topRegion = Bitmap.createBitmap(fullBitmap, 0, 0, fullWidth, fullHeight / 5);
                    saveImageForDebug(topRegion, "top_region");
                    
                    // Use the top region for OCR (as in original code)
                    Bitmap roiBitmap = topRegion;

                    // Recycle the full bitmap and unused regions as we no longer need them
                    fullBitmap.recycle();

                    // Use roiBitmap for OCR
                    ocr.run(roiBitmap, new OcrRunCallback() {
                        @Override
                        public void onSuccess(OcrResult result) {
                            // Save the OCR result image with bounding boxes
                            Bitmap imgWithBox = result.getImgWithBox();
                            if (imgWithBox != null) {
                                saveImageForDebug(imgWithBox, "ocr_result_with_boxes");
                            }
                            
                            // Process OCR results
                            lastText = processOcrResults(result);
                            List<OcrResultModel> outputRawResult = result.getOutputRawResult();
    
                            StringBuilder text = new StringBuilder("inferenceTime=" + result.getInferenceTime() + " ms\n");
                            
                            for (int index = 0; index < outputRawResult.size(); index++) {
                                OcrResultModel ocrResultModel = outputRawResult.get(index);
                                // Text orientation ocrResultModel.clsLabel can be "0" or "180"
                                text.append(index)
                                    .append("; confidence ")
                                    .append(ocrResultModel.getConfidence())
                                    .append("; points: ")
                                    .append(ocrResultModel.getPoints())
                                    .append("\n");
                            }                          

                            Log.d("OCR", "Processed text: " + lastText);
                            Log.d("OCR", "Raw processed data: " + text);
                            
                            // Clean up and reset flag
                            roiBitmap.recycle();
                            isRunning = false;
                        }

                        @Override
                        public void onFail(Throwable e) {
                            Log.e(TAG, "OCR processing failed!", e);
                            // Clean up and reset flag
                            roiBitmap.recycle();
                            isRunning = false;
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception in onImageAvailable", e);
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
        config.setCpuPowerMode(LitePowerMode.LITE_POWER_FULL);

        // Set precision mode for all models to FP16
        config.setRecRunPrecision(RunPrecision.LiteFp16);
        config.setDetRunPrecision(RunPrecision.LiteFp16);
        config.setClsRunPrecision(RunPrecision.LiteFp16);
        
        // Enable drawing of text position boxes for debugging
        config.setDrwwTextPositionBox(true);

        ocr.initModel(config, new OcrInitCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "OCR model initialization successful");
            }
            @Override
            public void onFail(Throwable e) {
                Log.e(TAG, "OCR model initialization failed", e);
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