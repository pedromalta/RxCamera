package com.ragnarok.rxcamera.example;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.ragnarok.rxcamera.RxCamera;
import com.ragnarok.rxcamera.RxCameraData;
import com.ragnarok.rxcamera.config.CameraUtil;
import com.ragnarok.rxcamera.config.RxCameraConfig;
import com.ragnarok.rxcamera.request.Func;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableObserver;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Example.MainActivity";

    private static final String[] REQUEST_PERMISSIONS = new String[] {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static final int REQUEST_PERMISSION_CODE = 233;

    private TextureView textureView;
    private Button openCameraBtn;
    private Button closeCameraBtn;
    private TextView logTextView;
    private ScrollView logArea;

    private RxCamera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        textureView = (TextureView) findViewById(R.id.preview_surface);
        openCameraBtn = (Button) findViewById(R.id.open_camera);
        closeCameraBtn = (Button) findViewById(R.id.close_camera);
        logTextView = (TextView) findViewById(R.id.log_textview);
        logArea = (ScrollView) findViewById(R.id.log_area);

        openCameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!checkPermission()) {
                    requestPermission();
                } else {
                    openCamera();
                }

            }
        });

        closeCameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (camera != null) {
                    camera.closeCameraWithResult().subscribe(new Consumer<Boolean>() {
                        @Override
                        public void accept(Boolean aBoolean) {
                            showLog("close camera finished, success: " + aBoolean);
                        }
                    });
                }
            }
        });

        textureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!checkCamera()) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    final float x = event.getX();
                    final float y = event.getY();
                    final Rect rect = CameraUtil.transferCameraAreaFromOuterSize(new Point((int)x, (int)y),
                            new Point(textureView.getWidth(), textureView.getHeight()), 100);
                    List<Camera.Area> areaList = Collections.singletonList(new Camera.Area(rect, 1000));
                    Observable.zip(camera.action().areaFocusAction(areaList),
                            camera.action().areaMeterAction(areaList),
                            new BiFunction<RxCamera, RxCamera, Object>() {
                                @Override
                                public Object apply(RxCamera rxCamera, RxCamera rxCamera2) {
                                    return rxCamera;
                                }
                            }).subscribe(new DisposableObserver<Object>() {
                        @Override
                        public void onComplete() {

                        }

                        @Override
                        public void onError(Throwable e) {
                            showLog("area focus and metering failed: " + e.getMessage());
                        }

                        @Override
                        public void onNext(Object o) {
                            showLog(String.format("area focus and metering success, x: %s, y: %s, area: %s", x, y, rect.toShortString()));
                        }
                    });
                }
                return false;
            }
        });

    }

    private boolean checkPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        for (String permission : REQUEST_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, REQUEST_PERMISSIONS, REQUEST_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            }
        }
    }

    private void openCamera() {
        RxCameraConfig config = new RxCameraConfig.Builder()
                .useBackCamera()
                .setAutoFocus(true)
                .setPreferPreviewFrameRate(15, 30)
                .setPreferPreviewSize(new Point(640, 480), false)
                .setHandleSurfaceEvent(true)
                .build();
        Log.d(TAG, "config: " + config);
        RxCamera.open(this, config).flatMap(new Function<RxCamera, ObservableSource<RxCamera>>() {
            @Override
            public Observable<RxCamera> apply(RxCamera rxCamera) {
                showLog("isopen: " + rxCamera.isOpenCamera() + ", thread: " + Thread.currentThread());
                camera = rxCamera;
                return rxCamera.bindTexture(textureView);
            }
        }).flatMap(new Function<RxCamera, Observable<RxCamera>>() {
            @Override
            public Observable<RxCamera> apply(RxCamera rxCamera) {
                showLog("isbindsurface: " + rxCamera.isBindSurface() + ", thread: " + Thread.currentThread());
                return rxCamera.startPreview();
            }
        }).observeOn(AndroidSchedulers.mainThread()).subscribe(new DisposableObserver<RxCamera>() {
            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
                showLog("open camera error: " + e.getMessage());
            }

            @Override
            public void onNext(final RxCamera rxCamera) {
                camera = rxCamera;
                showLog("open camera success: " + camera);
                Toast.makeText(MainActivity.this, "Now you can tap to focus", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLog(String s) {
        Log.d(TAG, s);
        logTextView.append(s + "\n");
        logTextView.post(new Runnable() {
            @Override
            public void run() {
                logArea.fullScroll(View.FOCUS_DOWN);
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camera != null) {
            camera.closeCamera();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_show_log:
                toggleLogArea();
                break;
            case R.id.action_successive_data:
                requestSuccessiveData();
                break;
            case R.id.action_periodic_data:
                requestPeriodicData();
                break;
            case R.id.action_one_shot:
                requestOneShot();
                break;
            case R.id.action_take_picture:
                requestTakePicture();
                break;
            case R.id.action_zoom:
                actionZoom();
                break;
            case R.id.action_smooth_zoom:
                actionSmoothZoom();
                break;
            case R.id.action_open_flash:
                actionOpenFlash();
                break;
            case R.id.action_close_flash:
                actionCloseFlash();
                break;
            case R.id.action_face_detection:
                faceDetection();
                break;
            case R.id.action_switch_camera:
                switchCamera();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleLogArea() {
        if (logArea.getVisibility() == View.VISIBLE) {
            logArea.setVisibility(View.GONE);
        } else {
            logArea.setVisibility(View.VISIBLE);
        }
    }

    private void requestSuccessiveData() {
        if (!checkCamera()) {
            return;
        }
        camera.request().successiveDataRequest().subscribe(new Consumer<RxCameraData>() {
            @Override
            public void accept(RxCameraData rxCameraData) {
                showLog("successiveData, cameraData.length: " + rxCameraData.cameraData.length);
            }
        });
    }

    private void requestOneShot() {
        if (!checkCamera()) {
            return;
        }
        camera.request().oneShotRequest().subscribe(new Consumer<RxCameraData>() {
            @Override
            public void accept(RxCameraData rxCameraData) {
                showLog("one shot request, cameraData.length: " + rxCameraData.cameraData.length);
            }
        });
    }

    private void requestPeriodicData() {
        if (!checkCamera()) {
            return;
        }
        camera.request().periodicDataRequest(1000).subscribe(new Consumer<RxCameraData>() {
            @Override
            public void accept(RxCameraData rxCameraData) {
                showLog("periodic request, cameraData.length: " + rxCameraData.cameraData.length);
            }
        });
    }

    private void requestTakePicture() {
        if (!checkCamera()) {
            return;
        }
        camera.request().takePictureRequest(true, new Func() {
            @Override
            public void call() {
                showLog("Captured!");
            }
        }, 480, 640, ImageFormat.JPEG, true).subscribe(new Consumer<RxCameraData>() {
            @Override
            public void accept(RxCameraData rxCameraData) {
                String path = Environment.getExternalStorageDirectory() + "/test.jpg";
                File file = new File(path);
                Bitmap bitmap = BitmapFactory.decodeByteArray(rxCameraData.cameraData, 0, rxCameraData.cameraData.length);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                        rxCameraData.rotateMatrix, false);
                try {
                    file.createNewFile();
                    FileOutputStream fos = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                showLog("Save file on " + path);
            }
        });
    }

    private void actionZoom() {
        if (!checkCamera()) {
            return;
        }
        camera.action().zoom(10).subscribe(new DisposableObserver<RxCamera>() {
            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
                showLog("zoom error: " + e.getMessage());
            }

            @Override
            public void onNext(RxCamera rxCamera) {
                showLog("zoom success: " + rxCamera);
            }
        });
    }

    private void actionSmoothZoom() {
        if (!checkCamera()) {
            return;
        }
        camera.action().smoothZoom(10).subscribe(new DisposableObserver<RxCamera>() {
            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
                showLog("zoom error: " + e.getMessage());
            }

            @Override
            public void onNext(RxCamera rxCamera) {
                showLog("zoom success: " + rxCamera);
            }
        });
    }

    private void actionOpenFlash() {
        if (!checkCamera()) {
            return;
        }
        camera.action().flashAction(true).subscribe(new DisposableObserver<RxCamera>() {
            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
                showLog("open flash error: " + e.getMessage());
            }

            @Override
            public void onNext(RxCamera rxCamera) {
                showLog("open flash");
            }
        });
    }

    private void actionCloseFlash() {
        if (!checkCamera()) {
            return;
        }
        camera.action().flashAction(false).subscribe(new DisposableObserver<RxCamera>() {
            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
                showLog("close flash error: " + e.getMessage());
            }

            @Override
            public void onNext(RxCamera rxCamera) {
                showLog("close flash");
            }
        });
    }

    private void faceDetection() {
        camera.request().faceDetectionRequest().subscribe(new Consumer<RxCameraData>() {
            @Override
            public void accept(RxCameraData rxCameraData) {
                showLog("on face detection: " + rxCameraData.faceList);
            }
        });
    }

    private void switchCamera() {
        if (!checkCamera()) {
            return;
        }
        camera.switchCamera().subscribe(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean aBoolean) {
                showLog("switch camera result: " + aBoolean);
            }
        });
    }

    private boolean checkCamera() {
        if (camera == null || !camera.isOpenCamera()) {
            return false;
        }
        return true;
    }
}
