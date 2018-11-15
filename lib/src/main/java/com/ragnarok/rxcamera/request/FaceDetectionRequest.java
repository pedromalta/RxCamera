package com.ragnarok.rxcamera.request;

import android.hardware.Camera;

import com.ragnarok.rxcamera.RxCamera;
import com.ragnarok.rxcamera.RxCameraData;
import com.ragnarok.rxcamera.error.FaceDetectionNotSupportError;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;

/**
 * Created by ragnarok on 16/6/5.
 */
public class FaceDetectionRequest extends BaseRxCameraRequest implements Camera.FaceDetectionListener {

    private ObservableEmitter<RxCameraData> subscriber;

    public FaceDetectionRequest(RxCamera rxCamera) {
        super(rxCamera);
    }

    @Override
    public Observable<RxCameraData> get() {
        return Observable.create(new ObservableOnSubscribe<RxCameraData>() {
            @Override
            public void subscribe(ObservableEmitter<RxCameraData> subscriber) throws Exception {
                if (rxCamera.getNativeCamera().getParameters().getMaxNumDetectedFaces() > 0) {
                    FaceDetectionRequest.this.subscriber = subscriber;
                } else {
                    subscriber.onError(new FaceDetectionNotSupportError("Camera not support face detection"));
                }
            }
        }).doOnSubscribe(new Consumer<Disposable>() {
            @Override
            public void accept(Disposable disposable) throws Exception {
                rxCamera.getNativeCamera().setFaceDetectionListener(FaceDetectionRequest.this);
                rxCamera.getNativeCamera().startFaceDetection();
            }
        }).doOnDispose(new Action() {
            @Override
            public void run() {
                rxCamera.getNativeCamera().setFaceDetectionListener(null);
                rxCamera.getNativeCamera().stopFaceDetection();
            }
        });
    }

    @Override
    public void onFaceDetection(Camera.Face[] faces, Camera camera) {
        if (subscriber != null && !subscriber.isDisposed() && rxCamera.isOpenCamera()) {
            RxCameraData cameraData = new RxCameraData();
            cameraData.faceList = faces;
            subscriber.onNext(cameraData);
        }
    }
}
