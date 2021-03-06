package com.eyes.theia;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;
import org.opencv.theia.R;

import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

public class FdActivity extends Activity implements CvCameraViewListener2 {

    private static final String TAG = "OCVSample::Activity";
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    public static final int JAVA_DETECTOR = 0;
    private static final int TM_SQDIFF = 0;
    private static final int TM_SQDIFF_NORMED = 1;
    private static final int TM_CCOEFF = 2;
    private static final int TM_CCOEFF_NORMED = 3;
    private static final int TM_CCORR = 4;
    private static final int TM_CCORR_NORMED = 5;
    private static final int TRAIN_FRAMES = 10;
    private static final int TEST_WINDOW = 30;
    private static final int LONG_WINDOW = 1;


    private int learn_frames = 0;
    private Mat teplateR;
    private Mat teplateL;
    int method = 0;

    private MenuItem mItemFace50;
    private MenuItem mItemFace40;
    private MenuItem mItemFace30;
    private MenuItem mItemFace20;
    private MenuItem mItemType;

    private Mat mRgba;
    private Mat mGray;
    // matrix for zooming
    private Mat mZoomWindow;
    private Mat mZoomWindow2;

    private File mCascadeFile;
    private CascadeClassifier mJavaDetector;
    private CascadeClassifier mJavaDetectorEye;


    private int mDetectorType = JAVA_DETECTOR;
    private String[] mDetectorName;

    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;

    private CameraBridgeViewBase mOpenCvCameraView;

    private SeekBar mMethodSeekbar;
    private TextView mValue;

    double xCenter = -1;
    double yCenter = -1;

    private ArrayList<Point> leftTrain;
    private ArrayList<Point> rightTrain;
    private Point leftMean;
    private Point rightMean;
    private Point leftStd;
    private Point rightStd;

    private ArrayList<Point> leftTest;
    private ArrayList<Point> rightTest;

    private int totalWindow = 0;
    private int badWindow = 0;

    private TextView alertText;
    private TextView energyRate;

    private Context context = this;

    private long startTime = 0;
    private long endTime = 0;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");


                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(
                                R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir,
                                "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        // --------------------------------- load left eye
                        // classificator -----------------------------------
                        InputStream iser = getResources().openRawResource(
                                R.raw.haarcascade_lefteye_2splits);
                        File cascadeDirER = getDir("cascadeER",
                                Context.MODE_PRIVATE);
                        File cascadeFileER = new File(cascadeDirER,
                                "haarcascade_eye_right.xml");
                        FileOutputStream oser = new FileOutputStream(cascadeFileER);

                        byte[] bufferER = new byte[4096];
                        int bytesReadER;
                        while ((bytesReadER = iser.read(bufferER)) != -1) {
                            oser.write(bufferER, 0, bytesReadER);
                        }
                        iser.close();
                        oser.close();

                        mJavaDetector = new CascadeClassifier(
                                mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from "
                                    + mCascadeFile.getAbsolutePath());

                        mJavaDetectorEye = new CascadeClassifier(
                                cascadeFileER.getAbsolutePath());
                        if (mJavaDetectorEye.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetectorEye = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from "
                                    + mCascadeFile.getAbsolutePath());


                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }
                    mOpenCvCameraView.setCameraIndex(1);
                    mOpenCvCameraView.enableFpsMeter();
                    mOpenCvCameraView.enableView();

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

        //Log.i(TAG, "Instantiated new " + this.getClass());
    }

    public void makeInvisible() {
        runOnUiThread(new Runnable() {
            @Override
            public void run () {
                    alertText.setVisibility(View.INVISIBLE);
            }
        });
    }

    public void makeVisible() {
        runOnUiThread(new Runnable() {
            @Override
            public void run () {
                    alertText.setVisibility(View.VISIBLE);
            }
        });
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

        mMethodSeekbar = (SeekBar) findViewById(R.id.methodSeekBar);

        alertText = (TextView) findViewById(R.id.alert);
        energyRate = (TextView) findViewById(R.id.energyRate);

        startTime = System.currentTimeMillis();

        leftTrain = new ArrayList<Point>();
        rightTrain = new ArrayList<Point>();
        leftTest = new ArrayList<Point>();
        rightTest = new ArrayList<Point>();
        leftMean = new Point(0, 0);
        rightMean = new Point(0, 0);
        leftStd = new Point(0, 0);
        rightStd = new Point(0, 0);

        mMethodSeekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                method = progress;
                switch (method) {
                    case 0:
                        mValue.setText("TM_SQDIFF");
                        break;
                    case 1:
                        mValue.setText("TM_SQDIFF_NORMED");
                        break;
                    case 2:
                        mValue.setText("TM_CCOEFF");
                        break;
                    case 3:
                        mValue.setText("TM_CCOEFF_NORMED");
                        break;
                    case 4:
                        mValue.setText("TM_CCORR");
                        break;
                    case 5:
                        mValue.setText("TM_CCORR_NORMED");
                        break;
                }


            }
        });
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
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this,
                mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
//        mZoomWindow.release();
//        mZoomWindow2.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }

//        if (mZoomWindow == null || mZoomWindow2 == null)
//            CreateAuxiliaryMats();

        MatOfRect faces = new MatOfRect();

        if (mJavaDetector != null)
            mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2,
                    2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize),
                    new Size());

        Rect[] facesArray = faces.toArray();

        if (facesArray.length == 0) {
            leftTest.add(new Point(0,0));
            rightTest.add(new Point(0,0));
        }

//        for (int i = 0; i < facesArray.length; i++) {
        if (facesArray.length != 0) {
            int i = 0;
            Core.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(),
                    FACE_RECT_COLOR, 3);
            xCenter = (facesArray[i].x + facesArray[i].width + facesArray[i].x) / 2;
            yCenter = (facesArray[i].y + facesArray[i].y + facesArray[i].height) / 2;
            Point center = new Point(xCenter, yCenter);

            Core.circle(mRgba, center, 10, new Scalar(255, 0, 0, 255), 3);

            Core.putText(mRgba, "[" + center.x + "," + center.y + "]",
                    new Point(center.x + 20, center.y + 20),
                    Core.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255,
                            255));

            Rect r = facesArray[i];
            // compute the eye area
            Rect eyearea = new Rect(r.x + r.width / 8,
                    (int) (r.y + (r.height / 4.5)), r.width - 2 * r.width / 8,
                    (int) (r.height / 3.0));
            // split it
            Rect eyearea_right = new Rect(r.x + r.width / 16,
                    (int) (r.y + (r.height / 4.5)),
                    (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));
            Rect eyearea_left = new Rect(r.x + r.width / 16
                    + (r.width - 2 * r.width / 16) / 2,
                    (int) (r.y + (r.height / 4.5)),
                    (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));
            // draw the area - mGray is working grayscale mat, if you want to
            // see area in rgb preview, change mGray to mRgba
            Core.rectangle(mRgba, eyearea_left.tl(), eyearea_left.br(),
                    new Scalar(255, 0, 0, 255), 2);
            Core.rectangle(mRgba, eyearea_right.tl(), eyearea_right.br(),
                    new Scalar(255, 0, 0, 255), 2);

            if (learn_frames < TRAIN_FRAMES) {
                teplateR = get_template(mJavaDetectorEye, eyearea_right, 24, 1);
                teplateL = get_template(mJavaDetectorEye, eyearea_left, 24, 0);
                learn_frames++;
            } else if (learn_frames < 4 * TRAIN_FRAMES) {
                match_eye(eyearea_right, teplateR, method, 1);
                match_eye(eyearea_left, teplateL, method, 0);
                learn_frames++;
            } else if (learn_frames == 4 * TRAIN_FRAMES) {
                for (int j = 0; j < leftTrain.size(); j++) {
                    leftMean.x += leftTrain.get(j).x;
                    leftMean.y += leftTrain.get(j).y;
                }
                leftMean.x /= leftTrain.size();
                leftMean.y /= leftTrain.size();
                for (int j = 0; j < leftTrain.size(); j++) {
                    leftStd.x += Math.pow((leftTrain.get(j).x - leftMean.x), 2);
                    leftStd.y += Math.pow((leftTrain.get(j).y - leftMean.y), 2);
                }
                leftStd.x = Math.pow((leftStd.x / leftTrain.size()), 0.5);
                leftStd.y = Math.pow((leftStd.y / leftTrain.size()), 0.5);

                for (int j = 0; j < rightTrain.size(); j++) {
                    rightMean.x += rightTrain.get(j).x;
                    rightMean.y += rightTrain.get(j).y;
                }
                rightMean.x /= rightTrain.size();
                rightMean.y /= rightTrain.size();
                for (int j = 0; j < rightTrain.size(); j++) {
                    rightStd.x += Math.pow((rightTrain.get(j).x - rightMean.x), 2);
                    rightStd.y += Math.pow((rightTrain.get(j).y - rightMean.y), 2);
                }
                rightStd.x = Math.pow((rightStd.x / rightTrain.size()), 0.5);
                rightStd.y = Math.pow((rightStd.y / rightTrain.size()), 0.5);

                learn_frames++;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run () {
                        Toast.makeText(context, "Calibration Done", Toast.LENGTH_SHORT).show();
                    }
                });

            } else {
                // Learning finished, use the new templates for template
                // matching
                
                match_eye(eyearea_right, teplateR, method, 1);
                match_eye(eyearea_left, teplateL, method, 0);

            }

//            if (eyearea_left.area() > 0 && eyearea_right.area() > 0) {
//                // cut eye areas and put them to zoom windows
//                Imgproc.resize(mRgba.submat(eyearea_left), mZoomWindow2,
//                        mZoomWindow2.size());
//                Imgproc.resize(mRgba.submat(eyearea_right), mZoomWindow,
//                        mZoomWindow.size());
//            }

        }

        if (leftTest.size() >= TEST_WINDOW && rightTest.size() >= TEST_WINDOW) {
            double leftX = 0;
            double leftY = 0;
            double rightX = 0;
            double rightY = 0;
            for (int i = 0; i < TEST_WINDOW; i++) {
                leftX += leftTest.get(i).x;
                leftY += leftTest.get(i).y;
                rightX += rightTest.get(i).x;
                rightY += rightTest.get(i).y;
            }
            if ((Math.abs(leftX / TEST_WINDOW - leftMean.x) +
                    Math.abs(leftY / TEST_WINDOW - leftMean.y) +
                    Math.abs(rightX / TEST_WINDOW - rightMean.x) +
                    Math.abs(rightY / TEST_WINDOW - rightMean.y)) > 2 * (leftStd.x + leftStd.y + rightStd.x + rightStd.y)) {
//                new PlayAlert(this).execute(null, null, null);
                MediaPlayer mp = MediaPlayer.create(this, R.raw.alarm);
                System.out.println("trigger alarm");
                mp.start();
                makeVisible();
                badWindow++;
            }
            leftTest = new ArrayList<Point>();
            rightTest = new ArrayList<Point>();
            totalWindow++;
            runOnUiThread(new Runnable() {
                @Override
                public void run () {
                    energyRate.setText(Math.round((1-(float)badWindow / (float)totalWindow)*100)+" %");
                }
            });
            endTime = System.currentTimeMillis();
            if ((endTime - startTime)/(60*1000) > LONG_WINDOW && (float)badWindow / (float)totalWindow >0.8) {
                //do alert
                runOnUiThread(new Runnable() {
                    @Override
                    public void run () {
                        alertText.setText("Stop Driving!");
                    }
                });
            }

            try {
                Thread.sleep(3000);                 //1000 milliseconds is one second.
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            makeInvisible();
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
        }
        return true;
    }

    private void setMinFaceSize(float faceSize) {
        mRelativeFaceSize = faceSize;
        mAbsoluteFaceSize = 0;
    }


    private void CreateAuxiliaryMats() {
        if (mGray.empty())
            return;

        int rows = mGray.rows();
        int cols = mGray.cols();

        if (mZoomWindow == null) {
            mZoomWindow = mRgba.submat(rows / 2 + rows / 10, rows, cols / 2
                    + cols / 10, cols);
            mZoomWindow2 = mRgba.submat(0, rows / 2 - rows / 10, cols / 2
                    + cols / 10, cols);
        }

    }

    private void match_eye(Rect area, Mat mTemplate, int type, int side) {
        Point matchLoc;
        Mat mROI = mGray.submat(area);
        int result_cols = mROI.cols() - mTemplate.cols() + 1;
        int result_rows = mROI.rows() - mTemplate.rows() + 1;
        // Check for bad template size
        if (mTemplate.cols() <= 0 || mTemplate.rows() <= 0) {
            return ;
        }
        if (result_cols <= 0 || result_rows <= 0)
            return ;
        Mat mResult = new Mat(result_cols, result_rows, CvType.CV_8U);

        switch (type) {
            case TM_SQDIFF:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_SQDIFF);
                break;
            case TM_SQDIFF_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult,
                        Imgproc.TM_SQDIFF_NORMED);
                break;
            case TM_CCOEFF:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCOEFF);
                break;
            case TM_CCOEFF_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult,
                        Imgproc.TM_CCOEFF_NORMED);
                break;
            case TM_CCORR:
                Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCORR);
                break;
            case TM_CCORR_NORMED:
                Imgproc.matchTemplate(mROI, mTemplate, mResult,
                        Imgproc.TM_CCORR_NORMED);
                break;
        }

        Core.MinMaxLocResult mmres = Core.minMaxLoc(mResult);
        // there is difference in matching methods - best match is max/min value
        if (type == TM_SQDIFF || type == TM_SQDIFF_NORMED) {
            matchLoc = mmres.minLoc;
        } else {
            matchLoc = mmres.maxLoc;
        }

        Point matchLoc_tx = new Point(matchLoc.x + area.x, matchLoc.y + area.y);
        Point matchLoc_ty = new Point(matchLoc.x + mTemplate.cols() + area.x,
                matchLoc.y + mTemplate.rows() + area.y);
        Core.rectangle(mRgba, matchLoc_tx, matchLoc_ty, new Scalar(255, 255, 0, 255));
        Core.circle(mRgba, new Point(matchLoc.x+mTemplate.cols()/2+area.x, matchLoc.y + mTemplate.rows()/2 + area.y), 2, new Scalar(255, 255, 255, 255), 2);

        if (learn_frames < 4 * TRAIN_FRAMES) {
            Point eyePoint = new Point(matchLoc.x, matchLoc.y);
            if (side == 0) {
                leftTrain.add(eyePoint);
            } else {
                rightTrain.add(eyePoint);
            }
        } else if (side == 0 && leftTest.size() < TEST_WINDOW) {
            leftTest.add(new Point(matchLoc.x, matchLoc.y));
        } else if (side == 1 && rightTest.size() < TEST_WINDOW) {
            rightTest.add(new Point(matchLoc.x, matchLoc.y));
        }
        Log.d("eye point", Double.toString(matchLoc.x));
    }

    private Mat get_template(CascadeClassifier clasificator, Rect area, int size, int side) {
        Mat template = new Mat();
        Mat mROI = mGray.submat(area);
        MatOfRect eyes = new MatOfRect();
        Point iris = new Point();
        Rect eye_template = new Rect();
        clasificator.detectMultiScale(mROI, eyes, 1.15, 2,
                Objdetect.CASCADE_FIND_BIGGEST_OBJECT
                        | Objdetect.CASCADE_SCALE_IMAGE, new Size(30, 30),
                new Size());

        Rect[] eyesArray = eyes.toArray();
        for (int i = 0; i < eyesArray.length;) {
            Rect e = eyesArray[i];
            e.x = area.x + e.x;
            e.y = area.y + e.y;
            Rect eye_only_rectangle = new Rect((int) e.tl().x,
                    (int) (e.tl().y + e.height * 0.4), (int) e.width,
                    (int) (e.height * 0.6));
            mROI = mGray.submat(eye_only_rectangle);
            Mat vyrez = mRgba.submat(eye_only_rectangle);

            Core.MinMaxLocResult mmG = Core.minMaxLoc(mROI);

            Core.circle(vyrez, mmG.minLoc, 2, new Scalar(255, 255, 255, 255), 2);
            iris.x = mmG.minLoc.x + eye_only_rectangle.x;
            iris.y = mmG.minLoc.y + eye_only_rectangle.y;

            Point eyePoint = new Point(iris.x-area.x, iris.y-area.y);
//            if (side == 0) {
//                leftTrain.add(eyePoint);
//            } else {
//                rightTrain.add(eyePoint);
//            }

            eye_template = new Rect((int) iris.x - size / 2, (int) iris.y
                    - size / 2, size, size);
            Core.rectangle(mRgba, eye_template.tl(), eye_template.br(),
                    new Scalar(255, 0, 0, 255), 2);
            template = (mGray.submat(eye_template)).clone();
            return template;
        }
        return template;
    }

    public void onRecreateClick(View v)
    {
        learn_frames = 0;
        leftTrain = new ArrayList<Point>();
        rightTrain = new ArrayList<Point>();
        leftTest = new ArrayList<Point>();
        rightTest = new ArrayList<Point>();
        leftMean = new Point(0, 0);
        rightMean = new Point(0, 0);
        leftStd = new Point(0, 0);
        rightStd = new Point(0, 0);

        makeInvisible();
        runOnUiThread(new Runnable() {
            @Override
            public void run () {
                energyRate.setText("100 %");

            }
        });
        startTime = System.currentTimeMillis();
    }
}