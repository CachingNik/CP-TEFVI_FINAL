package com.example.examples.detection;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import com.example.examples.detection.customview.OverlayView;
import com.example.examples.detection.env.BorderedText;
import com.example.examples.detection.env.ImageUtils;
import com.example.examples.detection.env.Logger;
import com.example.examples.detection.tflite.Classifier;
import com.example.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import com.example.examples.detection.tracking.MultiBoxTracker;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import com.example.thirdeye.examples.detection.R;


public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
	private static final Logger LOGGER = new Logger();
	// Configuration values for the prepackaged SSD model.
	private static final int TF_OD_API_INPUT_SIZE = 300;
	private static final boolean TF_OD_API_IS_QUANTIZED = true;
	private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
	private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
	private static final DetectorMode MODE = DetectorMode.TF_OD_API;
	// Minimum detection confidence to track a detection.
	private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
	private static final boolean MAINTAIN_ASPECT = false;
	private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
	private static final boolean SAVE_PREVIEW_BITMAP = false;
	private static final float TEXT_SIZE_DIP = 10;
	OverlayView trackingOverlay;
	private Integer sensorOrientation;
	private String TAG = "[DetectorActivity]";
	private Classifier detector;
	
	private long lastProcessingTimeMs;
	private Bitmap rgbFrameBitmap = null;
	private Bitmap croppedBitmap = null;
	private Bitmap cropCopyBitmap = null;
	
	private boolean computingDetection = false;
	
	private long timestamp = 0;
	
	private Matrix frameToCropTransform;
	private Matrix cropToFrameTransform;
	
	private MultiBoxTracker tracker;
	
	private BorderedText borderedText;

//    //spatial sound stuff--------------------------------------------
//    private static final String OBJECT_SOUND_FILE = "cube_sound.wav";
//    private static final String SUCCESS_SOUND_FILE = "success.wav";
//    private GvrAudioEngine gvrAudioEngine;
//    private volatile int sourceId = GvrAudioEngine.INVALID_ID;
//    private volatile int successSourceId = GvrAudioEngine.INVALID_ID;
//    //---------------------------------------------------------------
	
	@Override
	public void onPreviewSizeChosen(final Size size, final int rotation) {
		final float textSizePx =
						TypedValue.applyDimension(
										TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
		borderedText = new BorderedText(textSizePx);
		borderedText.setTypeface(Typeface.MONOSPACE);
		
		tracker = new MultiBoxTracker(this);
//        gvrAudioEngine = new GvrAudioEngine(this, GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);
//        load3dSoundEngine();
		
		int cropSize = TF_OD_API_INPUT_SIZE;
		
		try {
			detector =
							TFLiteObjectDetectionAPIModel.create(
											getAssets(),
											TF_OD_API_MODEL_FILE,
											TF_OD_API_LABELS_FILE,
											TF_OD_API_INPUT_SIZE,
											TF_OD_API_IS_QUANTIZED);
			cropSize = TF_OD_API_INPUT_SIZE;
		} catch (final IOException e) {
			e.printStackTrace();
			LOGGER.e(e, "Exception initializing classifier!");
			Toast toast =
							Toast.makeText(
											getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
			toast.show();
			finish();
		}
		
		previewWidth = size.getWidth();
		previewHeight = size.getHeight();
		
		sensorOrientation = rotation - getScreenOrientation();
		LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);
		
		LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
		rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
		croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);
		
		frameToCropTransform =
						ImageUtils.getTransformationMatrix(
										previewWidth, previewHeight,
										cropSize, cropSize,
										sensorOrientation, MAINTAIN_ASPECT);
		
		cropToFrameTransform = new Matrix();
		frameToCropTransform.invert(cropToFrameTransform);
		
		trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
		trackingOverlay.addCallback(
						new OverlayView.DrawCallback() {
							@Override
							public void drawCallback(final Canvas canvas) {
								tracker.draw(canvas);
								if (isDebug()) {
									tracker.drawDebug(canvas);
								}
							}
						});
		
		tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
		
	}
//    public void load3dSoundEngine() {
//        new Thread(() -> {
//                    System.out.println("THIK HAI");
//
//                    gvrAudioEngine.preloadSoundFile(OBJECT_SOUND_FILE);
//                    sourceId = gvrAudioEngine.createSoundObject(OBJECT_SOUND_FILE);
//                    gvrAudioEngine.setHeadRotation(1, 0, 0, 0);
//                    gvrAudioEngine.setHeadPosition(0, 0, 0);
//                    gvrAudioEngine.setSoundObjectPosition(sourceId, 0, 0, 1);
//                    gvrAudioEngine.playSound(sourceId, true /* looped playback */);
//                    try {
//                        synchronized(this){
//                            wait(3000);
//                        }
//                    }
//                    catch(InterruptedException ex){
//                    }
//                    System.out.println("THIK HAI  pura");
////                    gvrAudioEngine.preloadSoundFile(SUCCESS_SOUND_FILE);
//                })
//                .start();
//        LOGGER.i("THIK HAI");
//    }
// spatial sound------------------------------------------------------
//    @Override
//    public void onResume() {
//        super.onResume();
//        gvrAudioEngine.resume();
//    }
//    @Override
//    public void onPause() {
//        gvrAudioEngine.pause();
//        super.onPause();
//    }
//    @Override
//    public void onDrawEye(Eye eye) {}
//    @Override
//    public void onFinishFrame(Viewport viewport) {}
//    @Override
//    public void onSurfaceChanged(int i, int i1) {
//        Log.i(TAG, "onSurfaceChanged");
//    }
//    @Override
//    public void onRendererShutdown() {
//        Log.i(TAG, "onRendererShutdown");
//    }
//    @Override
//    public void onNewFrame(HeadTransform headTransform) {
////        gvrAudioEngine.setHeadRotation(0, 0, 0, 0);
//        // Regular update call to GVR audio engine.
////        gvrAudioEngine.update();
//    }
//------------------------------------------------------------------
	
	@Override
	protected void processImage() {
		++timestamp;
		final long currTimestamp = timestamp;
		trackingOverlay.postInvalidate();
		
		// No mutex needed as this method is not reentrant.
		if (computingDetection) {
			readyForNextImage();
			return;
		}
		computingDetection = true;
		LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");
		
		rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
		
		readyForNextImage();
		
		final Canvas canvas = new Canvas(croppedBitmap);
		canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
		// For examining the actual TF input.
		if (SAVE_PREVIEW_BITMAP) {
			ImageUtils.saveBitmap(croppedBitmap);
		}
		
		runInBackground(
						new Runnable() {
							@Override
							public void run() {
								LOGGER.i("Running detection on image " + currTimestamp);
								final long startTime = SystemClock.uptimeMillis();
								final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
								lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
								
								cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
								final Canvas canvas = new Canvas(cropCopyBitmap);
								final Paint paint = new Paint();
								paint.setColor(Color.RED);
								paint.setStyle(Style.STROKE);
								paint.setStrokeWidth(2.0f);
								
								float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
								switch (MODE) {
									case TF_OD_API:
										minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
										break;
								}
								
								final List<Classifier.Recognition> mappedRecognitions =
												new LinkedList<>();
								
								for (final Classifier.Recognition result : results) {
									final RectF location = result.getLocation();
									if (location != null && result.getConfidence() >= minimumConfidence) {
										canvas.drawRect(location, paint);
										
										cropToFrameTransform.mapRect(location);
										String s = "(" + String.valueOf(location.centerX()) + "," + String.valueOf(location.centerY()) + ")";
										LOGGER.i("detected object is: %s,%s", result.getTitle(), s);
										result.setLocation(location);
										mappedRecognitions.add(result);
									}
								}
								
								tracker.trackResults(mappedRecognitions, currTimestamp);
								trackingOverlay.postInvalidate();
								
								computingDetection = false;
								
								runOnUiThread(
												() -> {
													showFrameInfo(previewWidth + "x" + previewHeight);
													showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
													showInference(lastProcessingTimeMs + "ms");
												});
							}
						});
	}
	
	@Override
	protected int getLayoutId() {
		return R.layout.tfe_od_camera_connection_fragment_tracking;
	}
	
	@Override
	protected Size getDesiredPreviewFrameSize() {
		return DESIRED_PREVIEW_SIZE;
	}


//    @Override
//    public void onSurfaceCreated(EGLConfig eglConfig) {
//        Log.i(TAG, "onSurfaceCreated");
//        new Thread(
//                () -> {
//                    // Start spatial audio playback of OBJECT_SOUND_FILE at the model position. The
//                    // returned sourceId handle is stored and allows for repositioning the sound object
//                    // whenever the cube position changes.
//                    gvrAudioEngine.preloadSoundFile(OBJECT_SOUND_FILE);
//                    sourceId = gvrAudioEngine.createSoundObject(OBJECT_SOUND_FILE);
//                    gvrAudioEngine.setHeadRotation(1, 0, 0, 0);
//                    gvrAudioEngine.setHeadPosition(0, 0, 0);
//                    gvrAudioEngine.setSoundObjectPosition(sourceId, 0, 0, 1);
//                    gvrAudioEngine.playSound(sourceId, true /* looped playback */);
//                    // Preload an unspatialized sound to be played on a successful trigger on the cube.
////                    gvrAudioEngine.preloadSoundFile(SUCCESS_SOUND_FILE);
//                })
//            .start();
//    }
	
	// Which detection model to use: by default uses Tensorflow Object Detection API frozen
	// checkpoints.
	private enum DetectorMode {
		TF_OD_API;
	}
	
	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.plus) {
			String threads = threadsTextView.getText().toString().trim();
			int numThreads = Integer.parseInt(threads);
			if (numThreads >= 9) return;
			numThreads++;
			threadsTextView.setText(String.valueOf(numThreads));
			setNumThreads(numThreads);
//			gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
//			successSourceId = gvrAudioEngine.createStereoSound(SUCCESS_SOUND_FILE);
		} else if (v.getId() == R.id.minus) {
			String threads = threadsTextView.getText().toString().trim();
			int numThreads = Integer.parseInt(threads);
			if (numThreads == 1) {
				return;
			}
			numThreads--;
			threadsTextView.setText(String.valueOf(numThreads));
			setNumThreads(numThreads);
		} else if (v.getId() == R.id.play_sound) {
			++timestamp;
			final long currTimestamp = timestamp;
//            trackingOverlay.postInvalidate();
			
			// No mutex needed as this method is not reentrant.
//            if (computingDetection) {
//                readyForNextImage();
//                return;
//            }
			computingDetection = true;
//            LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

//            rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

//            readyForNextImage();

//            final Canvas canvas = new Canvas(croppedBitmap);
//            canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
			// For examining the actual TF input.
//            if (SAVE_PREVIEW_BITMAP) {
//                ImageUtils.saveBitmap(croppedBitmap);
//            }
			
			runInBackground(
							new Runnable() {
								@Override
								public void run() {
									LOGGER.i("Running detection on image ");
									final long startTime = SystemClock.uptimeMillis();
									final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
									lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
									
									cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
									final Canvas canvas = new Canvas(cropCopyBitmap);
									final Paint paint = new Paint();
//                            paint.setColor(Color.RED);
//                            paint.setStyle(Style.STROKE);
//                            paint.setStrokeWidth(2.0f);
									
									float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
									switch (MODE) {
										case TF_OD_API:
											minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
											break;
									}
									
									final List<Classifier.Recognition> mappedRecognitions =
													new LinkedList<>();
									
									for (final Classifier.Recognition result : results) {
										final RectF location = result.getLocation();
										if (location != null && result.getConfidence() >= minimumConfidence) {
											canvas.drawRect(location, paint);
											
											cropToFrameTransform.mapRect(location);
											LOGGER.i("xxx obj: %s, y loc is: (" + result.getTitle() + String.valueOf(-(location.centerY() - 240) * 7f / 240) + "," + String.valueOf(-(location.centerX() - 320) * 7f / 320) + ")");
											result.setLocation(location);
											LOGGER.i("xxx  "+result.getTitle());
//											if("mouse".equals(result.getTitle())){
//												LOGGER.i("xxxx  mouse");
//												gvrAudioEngine.preloadSoundFile("mouse.mp3");
//												successSourceId = gvrAudioEngine.createSoundObject("mouse.mp3");
//											}else {
//												LOGGER.i("xxxx  others");
												gvrAudioEngine.preloadSoundFile(result.getTitle()+".mp3");
												successSourceId = gvrAudioEngine.createSoundObject(result.getTitle()+".mp3");
//											}
											gvrAudioEngine.setSoundObjectPosition(successSourceId, -(location.centerY() - 240) * 7f / 240, -(location.centerX() - 320) * 7f / 320, 2);
											gvrAudioEngine.update();
											gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
//                                    mappedRecognitions.add(result);
											try {
												synchronized (this) {
													wait(1000);
												}
											} catch (InterruptedException ex) {
											}
										}
									}
									
									tracker.trackResults(mappedRecognitions, currTimestamp);
									trackingOverlay.postInvalidate();
									
									computingDetection = false;
									
									runOnUiThread(
													() -> {
														showFrameInfo(previewWidth + "x" + previewHeight);
														showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
														showInference(lastProcessingTimeMs + "ms");
													});
								}
							});
//            gvrAudioEngine.playSound(successSourceId, false /* looping disabled */);
//            successSourceId = gvrAudioEngine.createStereoSound(SUCCESS_SOUND_FILE);
		}
	}
	
	@Override
	protected void setUseNNAPI(final boolean isChecked) {
		runInBackground(() -> detector.setUseNNAPI(isChecked));
	}
	
	@Override
	protected void setNumThreads(final int numThreads) {
		runInBackground(() -> detector.setNumThreads(numThreads));
	}
}
