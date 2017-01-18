package com.ragnarok.rxcamera.request;

import com.ragnarok.rxcamera.OnRxCameraPreviewFrameCallback;
import com.ragnarok.rxcamera.RxCamera;
import com.ragnarok.rxcamera.RxCameraData;
import com.ragnarok.rxcamera.error.CameraDataNullException;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

/**
 * Created by ragnarok on 15/11/22.
 */
public class TakeOneShotRequest extends BaseRxCameraRequest implements OnRxCameraPreviewFrameCallback {

    private ObservableEmitter<RxCameraData> subscriber = null;

    public TakeOneShotRequest(RxCamera rxCamera) {
        super(rxCamera);
    }

    @Override
    public Observable<RxCameraData> get() {
        return Observable.create(new ObservableOnSubscribe<RxCameraData>() {
            @Override
            public void subscribe(ObservableEmitter<RxCameraData> subscriber) throws Exception {
                TakeOneShotRequest.this.subscriber = subscriber;
            }
        }).doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable disposable) throws Exception {
                rxCamera.installOneShotPreviewCallback(TakeOneShotRequest.this);
            }
        });
    }

    @Override
    public void onPreviewFrame(byte[] data) {
        if (subscriber != null && !subscriber.isDisposed() && rxCamera.isOpenCamera()) {
            if (data == null || data.length == 0) {
                subscriber.onError(new CameraDataNullException());
            }
            RxCameraData rxCameraData = new RxCameraData();
            rxCameraData.cameraData = data;
            rxCameraData.rotateMatrix = rxCamera.getRotateMatrix();
            subscriber.onNext(rxCameraData);
        }
    }
}
