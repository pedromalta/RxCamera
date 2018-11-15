package com.ragnarok.rxcamera.request;

import com.ragnarok.rxcamera.OnRxCameraPreviewFrameCallback;
import com.ragnarok.rxcamera.RxCamera;
import com.ragnarok.rxcamera.RxCameraData;
import com.ragnarok.rxcamera.error.CameraDataNullException;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;


/**
 * Created by ragnarok on 15/11/13.
 */
public class SuccessiveDataRequest extends BaseRxCameraRequest implements OnRxCameraPreviewFrameCallback {

    private boolean isInstallSuccessivePreviewCallback = false;

    private ObservableEmitter<RxCameraData> successiveDataSubscriber = null;

    public SuccessiveDataRequest(RxCamera rxCamera) {
        super(rxCamera);
    }

    public Observable<RxCameraData> get() {

        return Observable.create(new ObservableOnSubscribe<RxCameraData>() {
            @Override
            public void subscribe(final ObservableEmitter<RxCameraData> subscriber) throws Exception {
                successiveDataSubscriber = subscriber;
            }
        }).doOnDispose(new Action() {
            @Override
            public void run() {
                rxCamera.uninstallPreviewCallback(SuccessiveDataRequest.this);
                isInstallSuccessivePreviewCallback = false;
            }
        }).doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable disposable) throws Exception {
                if (!isInstallSuccessivePreviewCallback) {
                    rxCamera.installPreviewCallback(SuccessiveDataRequest.this);
                    isInstallSuccessivePreviewCallback = true;
                }
            }
        }).doOnTerminate(new Action() {
            @Override
            public void run() {
                rxCamera.uninstallPreviewCallback(SuccessiveDataRequest.this);
                isInstallSuccessivePreviewCallback = false;
            }
        });
    }

    @Override
    public void onPreviewFrame(byte[] data) {
        if (successiveDataSubscriber != null && !successiveDataSubscriber.isDisposed() && rxCamera.isOpenCamera()) {
            if (data == null || data.length == 0) {
                successiveDataSubscriber.onError(new CameraDataNullException());
            }
            RxCameraData cameraData = new RxCameraData();
            cameraData.cameraData = data;
            cameraData.rotateMatrix = rxCamera.getRotateMatrix();
            successiveDataSubscriber.onNext(cameraData);
        }
    }
}
