package org.opencv.samples.facedetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.Videoio;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class FdActivity extends Activity implements CvCameraViewListener2 {

	private static final String TAG = "OCVSample::Activity";
	private static final Scalar FACE_RECT_COLOR = new Scalar(0, 0, 255, 255);
	public static final int JAVA_DETECTOR = 0;
	public static final int NATIVE_DETECTOR = 1;

	public double bpm_final1 = 0;
	public int h[]= new int[256];
	public int contador_frames=0;
	private MenuItem mItemFace50;
	private MenuItem mItemFace40;
	private MenuItem mItemFace30;
	private MenuItem mItemFace20;
	private MenuItem mItemType;

	private Mat mRgba;
	private Mat mRgbaresize;
	private Mat mGray;
	private Mat imgYCC;
	private Mat skinRegion;
	private Mat roi;
	private Mat tmp;
	private File mCascadeFile;
	private CascadeClassifier mJavaDetector;
	private DetectionBasedTracker mNativeDetector;

	private int mDetectorType = JAVA_DETECTOR;
	private String[] mDetectorName;

	private float mRelativeFaceSize = 0.2f;
	private int mAbsoluteFaceSize = 0;
	private Rect eyearea = new Rect();
	long lStartTime = 0;
	Vector<Double> signalwDC = new Vector<Double>();
	Vector<Double> signal_normalized = new Vector<Double>();
	Vector<Double> spam = new Vector<Double>();
	Complex[] u;
	private CameraBridgeViewBase mOpenCvCameraView;

	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS: {
				Log.i(TAG, "OpenCV loaded successfully");

				// Load native library after(!) OpenCV initialization
				System.loadLibrary("detection_based_tracker");

				try {
					// load cascade file from application resources
					InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
					File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
					mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
					FileOutputStream os = new FileOutputStream(mCascadeFile);

					byte[] buffer = new byte[4096];
					int bytesRead;
					while ((bytesRead = is.read(buffer)) != -1) {
						os.write(buffer, 0, bytesRead);
					}
					is.close();
					os.close();

					mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
					if (mJavaDetector.empty()) {
						Log.e(TAG, "Failed to load cascade classifier");
						mJavaDetector = null;
					} else
						Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

					mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);

					cascadeDir.delete();

				} catch (IOException e) {
					e.printStackTrace();
					Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
				}

				mOpenCvCameraView.setMaxFrameSize(640, 480);

				mOpenCvCameraView.enableView();

				mOpenCvCameraView.enableFpsMeter();

			}
				break;
			default: {
				super.onManagerConnected(status);
			}
				break;
			}
		}
	};

	public FdActivity() {
		mDetectorName = new String[2];
		mDetectorName[JAVA_DETECTOR] = "Java";
		mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";

		Log.i(TAG, "Instantiated new " + this.getClass());
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.face_detect_surface_view);

		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
		mOpenCvCameraView.setCvCameraViewListener(this);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!OpenCVLoader.initDebug()) {
			Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
			OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
		} else {
			Log.d(TAG, "OpenCV library found inside package. Using it!");
			mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
		}
	}

	public void onDestroy() {
		super.onDestroy();
		mOpenCvCameraView.disableView();
	}

	public void onCameraViewStarted(int width, int height) {
		mGray = new Mat();
		mRgba = new Mat();
		mRgbaresize = new Mat();
		imgYCC = new Mat();
		skinRegion = new Mat();
		roi = new Mat();
		tmp = new Mat();
	}

	public void onCameraViewStopped() {
		mGray.release();
		mRgba.release();
		imgYCC.release();
	}

	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		
		for(int i=0;i<256;i++){
			if(i<12){
				h[i]=0;
				}
			if(i>=12 && i<=55){
				h[i]=1;	
			}
			if(i>55){
				h[i]=0;
			}			
		}

		if(contador_frames==0){
		
		mRgba = inputFrame.rgba();
		mGray = inputFrame.gray();

		if (mAbsoluteFaceSize == 0) {
			int height = mGray.rows();
			if (Math.round(height * mRelativeFaceSize) > 0) {
				mAbsoluteFaceSize = Math.round(height * (mRelativeFaceSize));
			}
			mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
		}

		MatOfRect faces = new MatOfRect();

		if (mDetectorType == JAVA_DETECTOR) {
			if (mJavaDetector != null)
				mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO:
																		// objdetect.CV_HAAR_SCALE_IMAGE
						new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
		} else if (mDetectorType == NATIVE_DETECTOR) {
			if (mNativeDetector != null)
				mNativeDetector.detect(mGray, faces);
		} else {
			Log.e(TAG, "Detection method is not selected!");
		}
		

		Rect[] facesArray = faces.toArray();

		for (int i = 0; i < facesArray.length; i++) { // recorremos el array
														// faces
			

			eyearea = new Rect(facesArray[i].x * (9 / 8) + facesArray[i].width - 3 * facesArray[i].width / 4,
					facesArray[i].y * (3 / 2), facesArray[i].width - 2 * facesArray[i].width / 4,
					facesArray[i].height - 4 * facesArray[i].height / 5);
			Imgproc.rectangle(mRgba, eyearea.tl(), eyearea.br(), new Scalar(255, 0, 0, 255), 2);
		}
	}
		
		else{
		
		mRgba = inputFrame.rgba();
		mGray = inputFrame.gray();

		if (mAbsoluteFaceSize == 0) {
			int height = mGray.rows();
			if (Math.round(height * mRelativeFaceSize) > 0) {
				mAbsoluteFaceSize = Math.round(height * (mRelativeFaceSize));
			}
			mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
		}

		MatOfRect faces = new MatOfRect();

		if (mDetectorType == JAVA_DETECTOR) {
			if (mJavaDetector != null)
				mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO:
																		// objdetect.CV_HAAR_SCALE_IMAGE
						new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
		} else if (mDetectorType == NATIVE_DETECTOR) {
			if (mNativeDetector != null)
				mNativeDetector.detect(mGray, faces);
		} else {
			Log.e(TAG, "Detection method is not selected!");
		}
		

		

			
			Imgproc.rectangle(mRgba, eyearea.tl(), eyearea.br(), new Scalar(255, 0, 0, 255), 2);
			}
		
		
	
			roi = mRgba.submat(eyearea); // seleccionamos la roi

			

			double media = 0;

			media = ROI.do_ROI(roi);
			Log.i(TAG, "VALOR_averageG: " + media + ""); // Esto nos da el valor
															// de media (G) de
															// la roi por frame.
			signalwDC.add(media); // Lo almacenamos en el vector signalwDC
									// (señal con DC)

			
		

		if (signalwDC.size() > 255) { // Si la señal contiene 25 elementos..
			long lEndTime = System.nanoTime();
			long difference_sec = lEndTime - lStartTime;
			Log.i(TAG, "TIME:" + (difference_sec / 1000000000.0));

			double time = difference_sec / 1000000000.0;

			Log.i(TAG, "TEST:");
			double sumatorio_vector = 0;
			double contador_vector = 0;
			double val_norm = 0;
			double mMaxFFTSample = 0;
			int mPeakPos = 0;

			Complex[] a = new Complex[256];
			double[] absSignal = new double[256 / 2];
			double[] bpm_vector = new double[256 / 2];
			double[] bpm = new double[256];
			double pre_bpm = 0;

			for (int l = 0; l < signalwDC.size(); l++) { // En este for
															// calculamos la
															// media de la señal
															// con DC

				sumatorio_vector = sumatorio_vector + signalwDC.elementAt(l);
				contador_vector++;

			}

			double media_vector = (sumatorio_vector / contador_vector); // En
																		// media_vector
																		// almacenamos
																		// el
																		// valor
																		// medio
																		// de la
																		// señal
																		// con
																		// DC
			Log.i(TAG, "VALOR_med_vec:" + media_vector + "");

			for (int o = 0; o < signalwDC.size(); o++) { // En este for tratamos
															// de eliminar la
															// DC,lo que hacemos
															// es restar la
															// media
															// a los componentes
															// del vector
				val_norm = signalwDC.get(o) - media_vector;
				signal_normalized.add(val_norm);
				Log.i(TAG, "VALOR_med_vec_norm:" + signal_normalized.elementAt(o) + ""); // la
																							// señal
																							// sin
																							// DC
																							// la
																							// almacenamos
																							// en
																							// signal_normalized

				a[o] = new Complex(signal_normalized.elementAt(o), 0.0);

			}

			u = FFT.fft(a);

			double fps = 256.0 / time; // FPS --> frame/seconds
			Log.i(TAG, "pruebaaa:" + fps);
			for (int z = 0; z < 256; z++) {

				pre_bpm = 60.0 * z * (fps / 256.0); // 60*(N(i))*(FPS/N)

				spam.add(pre_bpm);

			}

			

			for (int m = 0; m < (256 / 2); m++) {

				absSignal[m] = Math.sqrt(Math.pow(u[m].re(), 2) + Math.pow(u[m].im(), 2));

				bpm_vector[m] = absSignal[m];
				Log.i(TAG, "FFTValores:" + absSignal[m] + "");

				if (bpm_vector[m] > mMaxFFTSample && m > 15 && m < 45) {
					mMaxFFTSample = bpm_vector[m];
					mPeakPos = m;

				}

				absSignal[m] = 0;
				
			}

			Log.i(TAG, "BPM:" + spam.elementAt(mPeakPos) + "");
			double bpm_final = spam.elementAt(mPeakPos).intValue();
			bpm_final1 = bpm_final;
			showToast(null);

			mPeakPos = 0;
			mMaxFFTSample = 0;

			signalwDC.removeAllElements();
			signal_normalized.removeAllElements();
			spam.removeAllElements();

			lStartTime = System.nanoTime();

		}
		contador_frames++;
		if(contador_frames >255){
			contador_frames=0;
		}
		
		return mRgba;

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.i(TAG, "called onCreateOptionsMenu");
		mItemFace50 = menu.add("Face size 50%");
		mItemFace40 = menu.add("Face size 40%");
		mItemFace30 = menu.add("Face size 30%");
		mItemFace20 = menu.add("Face size 20%");
		mItemType = menu.add(mDetectorName[mDetectorType]);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
		if (item == mItemFace50)
			setMinFaceSize(0.5f);
		else if (item == mItemFace40)
			setMinFaceSize(0.4f);
		else if (item == mItemFace30)
			setMinFaceSize(0.3f);
		else if (item == mItemFace20)
			setMinFaceSize(0.2f);
		else if (item == mItemType) {
			int tmpDetectorType = (mDetectorType + 1) % mDetectorName.length;
			item.setTitle(mDetectorName[tmpDetectorType]);
			setDetectorType(tmpDetectorType);
		}
		return true;
	}

	private void setMinFaceSize(float faceSize) {

		mRelativeFaceSize = faceSize;
		mAbsoluteFaceSize = 0;
	}

	public void showToast(final String toast) {

		runOnUiThread(new Runnable() {
			public void run() {
				Toast.makeText(FdActivity.this, "BPM: " + bpm_final1, Toast.LENGTH_SHORT).show();
			}
		});
	}

	private void setDetectorType(int type) {
		if (mDetectorType != type) {
			mDetectorType = type;

			if (type == NATIVE_DETECTOR) {
				Log.i(TAG, "Detection Based Tracker enabled");
				mNativeDetector.start();
			} else {
				Log.i(TAG, "Cascade detector enabled");
				mNativeDetector.stop();
			}
		}
	}
}
