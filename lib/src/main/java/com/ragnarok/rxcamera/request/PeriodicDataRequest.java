package com.ragnarok.rxcamera.request;

import com.ragnarok.rxcamera.OnRxCameraPreviewFrameCallback;
import com.ragnarok.rxcamera.RxCamera;
import com.ragnarok.rxcamera.RxCameraData;
import com.ragnarok.rxcamera.error.CameraDataNullException;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by ragnarok on 15/11/15.
 */
public class PeriodicDataRequest extends BaseRxCameraRequest implements OnRxCameraPreviewFrameCallback {

    private static final String TAG = "MicroMsg.PeriodicDataRequest";

    private long intervalMills;

    private boolean isInstallCallback = false;

    private ObservableEmitter<RxCameraData> subscriber = null;

    private RxCameraData currentData = new RxCameraData();

    private long lastSendDataTimestamp = 0;

    public PeriodicDataRequest(RxCamera rxCamera, long intervalMills) {
        super(rxCamera);
        this.intervalMills = intervalMills;
    }

    @Override
    public Observable<RxCameraData> get() {
        return Observable.create(new ObservableOnSubscribe<RxCameraData>() {
            @Override
            public void subscribe(final ObservableEmitter<RxCameraData> subscriber) throws Exception {
                PeriodicDataRequest.this.subscriber = subscriber;
                subscriber.setDisposable(Schedulers.newThread().createWorker().schedulePeriodically(new Runnable() {
                    @Override
                    public void run() {
                        if (currentData.cameraData != null && !subscriber.isDisposed() && rxCamera.isOpenCamera()) {
                            subscriber.onNext(currentData);
                        }
                    }
                }, 0, intervalMills, TimeUnit.MILLISECONDS));

            }
        }).doOnDispose(new Action() {
            @Override
            public void run() {
                rxCamera.uninstallPreviewCallback(PeriodicDataRequest.this);
                isInstallCallback = false;
            }
        }).doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable disposable) throws Exception {
                if (!isInstallCallback) {
                    rxCamera.installPreviewCallback(PeriodicDataRequest.this);
                    isInstallCallback = true;
                }
            }
        }).doOnTerminate(new Action() {
            @Override
            public void run() {
                rxCamera.uninstallPreviewCallback(PeriodicDataRequest.this);
                isInstallCallback = false;
            }
        }).observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public void onPreviewFrame(byte[] data) {
        if (subscriber != null && !subscriber.isDisposed() && rxCamera.isOpenCamera()) {
            if (data == null || data.length == 0) {
                subscriber.onError(new CameraDataNullException());
            }
            currentData.cameraData = data;
            currentData.rotateMatrix = rxCamera.getRotateMatrix();
        }
    }
}
