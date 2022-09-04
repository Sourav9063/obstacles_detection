/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.classification;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Size;
import android.util.TypedValue;
import android.view.TextureView;
import android.view.ViewStub;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import org.tensorflow.lite.examples.classification.customview.AutoFitTextureView;
import org.tensorflow.lite.examples.classification.env.BorderedText;
import org.tensorflow.lite.examples.classification.env.Logger;
import org.tensorflow.lite.examples.classification.tflite.Classifier;
import org.tensorflow.lite.examples.classification.tflite.Classifier.Device;
import org.tensorflow.lite.examples.classification.tflite.Classifier.Model;
import org.tensorflow.lite.examples.classification.tflite.ClassifierFloatMobileNet;
import org.tensorflow.lite.examples.classification.tflite.ClassifierQuantizedEfficientNet;

import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.PixelFormat;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.OptionalDouble;

public class ClassifierActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final float TEXT_SIZE_DIP = 10;
    private Bitmap rgbFrameBitmap = null;
    private long lastProcessingTimeMs;
    private Integer sensorOrientation;
    private Classifier classifier;
    private BorderedText borderedText;
    private TextToSpeech textToSpeech;
    private Vibrator vibrator;
//    ArrayList<Float> tmpStore = new ArrayList<>();
//    ArrayList<Float> tmpmax = new ArrayList<>();
//    ArrayList<Float> tmpmin = new ArrayList<>();
    ArrayList<Float> tmpavrg = new ArrayList<>();

    float summax = 0;
    float summin = 0;
    float sum = 0;
    float sumLeft = 0;
    float sumRight = 0;


    /**
     * Input image size of the model along x axis.
     */
    private int imageSizeX;
    /**
     * Input image size of the model along y axis.
     */
    private int imageSizeY;
    private int count = 0;
    private float maxval = 0;
    private float minval = 100000000;

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_ic_camera_connection_fragment;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        recreateClassifier(getModel(), getDevice(), getNumThreads());
        if (classifier == null) {
            LOGGER.e("No classifier on preview!");
            return;
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    }

    @Override
    protected void processImage() {
        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
        final int cropSize = Math.min(previewWidth, previewHeight);

        runInBackground(

                new Runnable() {

                    @Override
                    public void run() {
                        if (classifier != null) {
                            final long startTime = SystemClock.uptimeMillis();
                            //final List<Classifier.Recognition> results =
                            //    classifier.recognizeImage(rgbFrameBitmap, sensorOrientation);
                            final List<Classifier.Recognition> results = new ArrayList<>();

                            float[] img_array = classifier.recognizeImage(rgbFrameBitmap, sensorOrientation);

//                            List<Classifier.Recognition> recognitions=classifier.recognizeImageObject(rgbFrameBitmap, sensorOrientation);
//                            System.out.println(classifier.labels.size());

//    for (String lebes:classifier.labels){
//        System.out.println(lebes);
//    }
//                            for(Classifier.Recognition recognition:recognitions){
//                                System.out.println(recognition.getTitle());
//                            }

                            count++;


              /*
              float maxval = Float.NEGATIVE_INFINITY;
              float minval = Float.POSITIVE_INFINITY;
              for (float cur : img_array) {
                maxval = Math.max(maxval, cur);
                minval = Math.min(minval, cur);
              }
              float multiplier = 0;
              if ((maxval - minval) > 0) multiplier = 255 / (maxval - minval);

              int[] img_normalized = new int[img_array.length];
              for (int i = 0; i < img_array.length; ++i) {
                float val = (float) (multiplier * (img_array[i] - minval));
                img_normalized[i] = (int) val;
              }



              TextureView textureView = findViewById(R.id.textureView3);
              //AutoFitTextureView textureView = (AutoFitTextureView) findViewById(R.id.texture);

              if(textureView.isAvailable()) {
                int width = imageSizeX;
                int height = imageSizeY;

                Canvas canvas = textureView.lockCanvas();
                canvas.drawColor(Color.BLUE);
                Paint paint = new Paint();
                paint.setStyle(Paint.Style.FILL);
                paint.setARGB(255, 150, 150, 150);

                int canvas_size = Math.min(canvas.getWidth(), canvas.getHeight());

                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

                for (int ii = 0; ii < width; ii++) //pass the screen pixels in 2 directions
                {
                  for (int jj = 0; jj < height; jj++) {
                    //int val = img_normalized[ii + jj * width];
                    int index = (width - ii - 1) + (height - jj - 1) * width;
                    if(index < img_array.length) {
                      int val = img_normalized[index];
                      bitmap.setPixel(ii, jj, Color.rgb(val, val, val));
                    }
                  }
                }

                canvas.drawBitmap(bitmap, null, new RectF(0, 0, canvas_size, canvas_size), null);

                textureView.unlockCanvasAndPost(canvas);

              }
              */

                            int centerPix = (int) Math.floor((img_array.length) / 2);
//
//            float [] sectionPix= Arrays.copyOfRange(img_array,centerPix-100,centerPix+100);
//
//            double sum= 0;
//            for(float val : sectionPix){
//              sum+=val;
//              }
//              System.out.println(sum);
//              System.out.println(sum/sectionPix.length);
                            System.out.println(img_array[0]);
                            System.out.println(img_array[centerPix]);
                            System.out.println(img_array[img_array.length - 1]);
//                System.out.println(img_array[65536-100]);


                            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                            LOGGER.v("Detect: %s", results);


                            for (float val : img_array) {
                                if (minval > val) minval = val;
                                if (maxval < val) maxval = val;
                            }
                            System.out.println("maxval " + maxval);
                            System.out.println("minval " + minval);
//                            tmpStore.add(img_array[centerPix - 256 / 2]);
                            sum+=img_array[centerPix - 256 / 2];
                            summax+=maxval;
                            summin+=minval;
                            sumLeft+=img_array[(centerPix - 256 / 2) + 20];
                            sumRight+=img_array[(centerPix - 256 / 2) - 20];
//                            tmpmax.add(maxval);
//                            tmpmin.add(minval);
                            img_array[centerPix - 256 / 2] = maxval;
                            img_array[(centerPix - 256 / 2) - 256 * 15] = maxval;
                            img_array[(centerPix - 256 / 2) + 256 * 15] = maxval;
                            img_array[(centerPix - 256 / 2) - 20] = maxval;
                            img_array[(centerPix - 256 / 2) + 20] = maxval;


                            minval = 100000000;
                            maxval = 0;
final int  maxCount=6;
                            if (count > maxCount) {


                                float avrg = sum / maxCount;
                                float avrgleft= sumLeft / maxCount;
                                float avrgRight= sumRight / maxCount;
                                float avrgmin = summin / maxCount;
                                float avrgmax = summax / maxCount;
                                System.out.println("avrg " + avrg);
                                System.out.println("avrgmax " + avrgmax);
                                System.out.println("avrgmin " + avrgmin);
                                float cal = (avrgmax + avrgmin) / 4 * 3;
                                float cal2 = (avrgmin + avrgmax) / 2;

                                System.out.println("cal " + cal);
                                System.out.println("cal2 " + cal2);

                                System.out.println("cal " + cal);
//                if(avrg<600&&avrg>400){
//                  Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
//                  v.vibrate(75);
//                }
//                else
                                if (avrg >= cal2 && avrg <= cal) {
                                    vibrator.vibrate(125);
                                    if(!textToSpeech.isSpeaking()){
                                        if(avrg>=cal&&avrgleft>=cal2&&avrgRight>=cal2){
                                            textToSpeech.speak("Stop", TextToSpeech.QUEUE_FLUSH, null);
                                        }
                                        else if(avrgleft>=cal2&&avrgRight<cal2){
                                            textToSpeech.speak("Caution left", TextToSpeech.QUEUE_FLUSH, null);
                                        }
                                        else if(avrgRight>=cal2&&avrgleft<cal2){
                                            textToSpeech.speak("Caution right", TextToSpeech.QUEUE_FLUSH, null);
                                        }
                                    }
                                } else if (avrg > cal) {
                                   vibrator.vibrate(150);
                                    if(!textToSpeech.isSpeaking()){
                                        if(avrg>=cal&&avrgleft>=cal&&avrgRight>=cal){
                                            textToSpeech.speak("Stop", TextToSpeech.QUEUE_FLUSH, null);
                                        }
                                        else if(avrgleft>=cal&&avrgRight<cal){
                                            textToSpeech.speak("Obstacle left", TextToSpeech.QUEUE_FLUSH, null);
                                        }
                                        else if(avrgRight>=cal&&avrgleft<cal){
                                            textToSpeech.speak("Obstacle right", TextToSpeech.QUEUE_FLUSH, null);
                                        }
                                    }



                                } else {
                                    vibrator.vibrate(10);
                                }

                                count = 0;
                                sum=0;
                                summax=0;
                                summin=0;
                                sumLeft=0;
                                sumRight=0;
                                System.out.println("200----------------------------------------------------------");

                            }

                            System.out.println(img_array.length);


                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            //showResultsInBottomSheet(results);
                                            showResultsInTexture(img_array, imageSizeX, imageSizeY);
                                            showFrameInfo(previewWidth + "x" + previewHeight);
                                            showCropInfo(imageSizeX + "x" + imageSizeY);
                                            showCameraResolution(cropSize + "x" + cropSize);
                                            showRotationInfo(String.valueOf(sensorOrientation));
                                            showInference(lastProcessingTimeMs + "ms");
                                        }
                                    });
                        }
                        readyForNextImage();
                    }
                });
    }

    @Override
    protected void onInferenceConfigurationChanged() {
        if (rgbFrameBitmap == null) {
            // Defer creation until we're getting camera frames.
            return;
        }
        final Device device = getDevice();
        final Model model = getModel();
        final int numThreads = getNumThreads();
        runInBackground(() -> recreateClassifier(model, device, numThreads));
    }

    private void recreateClassifier(Model model, Device device, int numThreads) {
        if (classifier != null) {
            LOGGER.d("Closing classifier.");
            classifier.close();
            classifier = null;
        }
        if (device == Device.GPU
                && (model == Model.QUANTIZED_MOBILENET || model == Model.QUANTIZED_EFFICIENTNET)) {
            LOGGER.d("Not creating classifier: GPU doesn't support quantized models.");
            runOnUiThread(
                    () -> {
                        Toast.makeText(this, R.string.tfe_ic_gpu_quant_error, Toast.LENGTH_LONG).show();
                    });
            return;
        }
        try {
            LOGGER.d(
                    "Creating classifier (model=%s, device=%s, numThreads=%d)", model, device, numThreads);
            classifier = Classifier.create(this, model, device, numThreads);
           vibrator= (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int i) {

                    // if No error is found then only it will run
                    if(i!=TextToSpeech.ERROR){
                        // To Choose language of speech
                        textToSpeech.setLanguage(Locale.US);
                    }
                }
            });

        } catch (IOException | IllegalArgumentException e) {
            LOGGER.e(e, "Failed to create classifier.");
            runOnUiThread(
                    () -> {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    });
            return;
        }

        // Updates the input image size.
        imageSizeX = classifier.getImageSizeX();
        imageSizeY = classifier.getImageSizeY();

//    System.out.println(classifier);

    }
}
