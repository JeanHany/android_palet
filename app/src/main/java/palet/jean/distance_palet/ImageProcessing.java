package palet.jean.distance_palet;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.opencv.core.Core.circle;
import static org.opencv.core.Core.clipLine;
import static org.opencv.core.Core.line;
import static org.opencv.core.Core.magnitude;
import static org.opencv.core.Core.rectangle;
import static org.opencv.imgproc.Imgproc.approxPolyDP;
import static org.opencv.imgproc.Imgproc.blur;
import static org.opencv.imgproc.Imgproc.boundingRect;
import static org.opencv.imgproc.Imgproc.cvtColor;
import static org.opencv.imgproc.Imgproc.drawContours;
import static org.opencv.imgproc.Imgproc.findContours;
import static org.opencv.imgproc.Imgproc.minEnclosingCircle;
import static org.opencv.imgproc.Imgproc.threshold;

/**
 * Created by Jean_jean on 9/15/2015.
 */
public class ImageProcessing extends Activity implements CameraBridgeViewBase.CvCameraViewListener {


    private String TAG = "Image processing";
    private CameraBridgeViewBase mOpenCvCameraView;
    private int thresh = 100;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };



    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_image);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, mLoaderCallback);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(Mat inputFrame) {
        Mat src_gray = new Mat();
        Mat threshold_output = new Mat();
        ArrayList<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        MatOfInt4 hierarchy = new MatOfInt4();
        Log.i(TAG, inputFrame.toString());
        cvtColor(inputFrame, src_gray, Imgproc.COLOR_BGR2GRAY);
        Size size = new Size(3, 3);
        blur( src_gray, src_gray, size );
        threshold( src_gray, threshold_output, thresh, 255, Imgproc.THRESH_BINARY );
        /// Find contours
        findContours( threshold_output, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0) );
        /// Approximate contours to polygons + get bounding rects and circles
        ArrayList<MatOfPoint2f> contours_poly = new ArrayList<MatOfPoint2f>();
        MatOfRect boundRect = new MatOfRect();
        MatOfPoint2f center = new MatOfPoint2f();
        MatOfFloat radius = new MatOfFloat( contours.size() );

        Log.i(TAG, "Size contour :" + Integer.toString(contours.size()));
        List<MatOfPoint2f> newContours = new ArrayList<MatOfPoint2f>();
        for(MatOfPoint point : contours) {
            MatOfPoint2f newPoint = new MatOfPoint2f(point.toArray());
            newContours.add(newPoint);
            contours_poly.add(newPoint);
        }

        List<MatOfPoint> newContours_poly = new ArrayList<MatOfPoint>();
        for(MatOfPoint2f point : contours_poly) {
            MatOfPoint newPoint = new MatOfPoint(point.toArray());
            newContours_poly.add(newPoint);
        }

        boundRect.alloc(contours.size());
        center.alloc(contours.size());
        radius.alloc(contours.size());
        Rect[] rect_array = boundRect.toArray();

        for( int i = 0; i < contours.size(); i++ )
        {
            approxPolyDP( newContours.get(i), contours_poly.get(i), 3, true );
            rect_array[i] = boundingRect(newContours_poly.get(i));
            minEnclosingCircle( contours_poly.get(i), center.toArray()[i], radius.toArray() );
        }

        boundRect.fromArray(rect_array);
        /// Draw polygonal contour + bonding rects + circles
        Mat drawing = new Mat( threshold_output.size(), CvType.CV_8UC3 );
        Scalar color = new Scalar( 180, 50, 60 );


        for( int i = 0; i< contours.size(); i++ )
        {
            drawContours( src_gray, newContours_poly, i, color, 1, 8, new MatOfInt4(), 0, new Point() );
            //rectangle( drawing, boundRect.toArray()[i].tl(), boundRect.toArray()[i].br(), color, 2, 8, 0 );
            if(radius.toArray()[i] >=0) {
                circle(src_gray, center.toArray()[i], (int) radius.toArray()[i], color, 2, 8, 0);
            }
        }
        Mat calcul = calcul_distance_palet(center, radius, src_gray, color);
        if(calcul != null) src_gray = calcul;
        return src_gray;
    }

    public Mat calcul_distance_palet(MatOfPoint2f center, MatOfFloat radius, Mat src_gray, Scalar color){
        Integer[] dista = new Integer[radius.toArray().length];
        ArrayList<Double> aire = new ArrayList<Double>(radius.toArray().length);
        MatOfFloat distance = new MatOfFloat();
        Point[] point_centre = center.toArray();
        float[] point_radius = radius.toArray();

        Log.i(TAG, "Point center :" + point_centre.length +" "+ point_radius.length);
        if(point_centre.length == point_radius.length  && !center.empty()) {
            for (int i = 0; i < point_centre.length; i++) {
                aire.add(point_radius[i] * Math.PI);
            }
            double min_aire = Collections.min(aire);
            int index = aire.indexOf(min_aire);
            MatOfFloat metre = new MatOfFloat(point_radius[index]);
            Log.i(TAG, "Siez metre :" +metre.size() +" Size center " + center.size());
            if(metre.size() == center.size()) {
                for (int i = 0; i < point_centre.length; i++) {
                    magnitude(metre, center, distance);
                }
            }
            int index1 = 0;
            if(!distance.empty()) {
                float[] dist = distance.toArray();
                ArrayList<Float> array_dist = new ArrayList<Float>();
                for (int i = 0; i < dist.length; i++) {
                    array_dist.add(dist[i] - point_radius[i]);
                }

                float min_dist = Collections.min(array_dist);
                index1 = array_dist.indexOf(min_dist);
            }
            Point x = center.toArray()[index];
            Point y = center.toArray()[index1];
            Log.i(TAG, "trace ligne between two points");
            line(src_gray, x, y, color);
        }
        return src_gray;
    }
}
