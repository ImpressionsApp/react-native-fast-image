package com.dylanvann.fastimage;

import java.io.*;
import java.util.HashMap;
import java.util.function.Consumer;
import android.app.Activity;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.target.Target;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.views.imagehelper.ImageSource;

class FastImageViewModule extends ReactContextBaseJavaModule {

    private static final String REACT_CLASS = "FastImageView";

    FastImageViewModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @ReactMethod
    public void preload(final ReadableArray sources, Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) return;

        PreloadResultHandler resultHandler = new PreloadResultHandler(promise, sources.size());

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < sources.size(); i++) {
                    final ReadableMap source = sources.getMap(i);
                    final FastImageSource imageSource = FastImageViewConverter.getImageSource(activity, source);
                    final String sourceString = hashMapToString(source.toHashMap());

                    System.out.println("preload: " + sourceString);

                    Glide
                            .with(activity.getApplicationContext())
                            // This will make this work for remote and local images. e.g.
                            //    - file:///
                            //    - content://
                            //    - res:/
                            //    - android.resource://
                            //    - data:image/png;base64
                            .load(
                                    imageSource.isBase64Resource() ? imageSource.getSource() :
                                    imageSource.isResource() ? imageSource.getUri() : imageSource.getGlideUrl()
                            )
                            .apply(FastImageViewConverter.getOptions(activity, imageSource, source))
                            .listener(new RequestListener() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target target, boolean isFirstResource) {
                                    System.out.println("preload: " + sourceString + " failed");
                                    resultHandler.handleResult(false);
                                    return false;
                                }
                                @Override
                                public boolean onResourceReady(Object resource, Object model, Target target, DataSource dataSource, boolean isFirstResource) {
                                    System.out.println("preload: " + sourceString + " ready");
                                    resultHandler.handleResult(true);
                                    return false;
                                }
                            })
                            .preload();
                }
            }
        });
    }

    @ReactMethod
    public void clearMemoryCache(final Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.resolve(null);
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Glide.get(activity.getApplicationContext()).clearMemory();
                promise.resolve(null);
            }
        });
    }

    @ReactMethod
    public void clearDiskCache(Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.resolve(null);
            return;
        }

        Glide.get(activity.getApplicationContext()).clearDiskCache();
        promise.resolve(null);
    }

    private String hashMapToString(HashMap<String, Object> map) {
        StringBuilder mapAsString = new StringBuilder("{");
        for (String key : map.keySet()) {
            mapAsString.append(key + "=" + map.get(key) + ", ");
        }
        mapAsString.delete(mapAsString.length()-2, mapAsString.length()).append("}");
        return mapAsString.toString();
    }
}

class PreloadResultHandler {
    private Promise promise;
    private int totalCount = 0;
    private int finishedCount = 0;
    private int skippedCount = 0;

    public PreloadResultHandler(Promise promise, int totalCount) {
        this.promise = promise;
        this.totalCount = totalCount;
    }

    // Create a synchronized method to avoid race conditions when incrementing counts across multiple Glide preload processes
    public synchronized void handleResult(boolean success) {
        if (success) {
            this.finishedCount += 1;
        } else {
            this.skippedCount += 1;
        }

        boolean donePreloading = this.finishedCount + this.skippedCount == this.totalCount;

        if (donePreloading) {
            WritableMap result = Arguments.createMap();
            result.putInt("finishedCount", this.finishedCount);
            result.putInt("skippedCount", this.skippedCount);
            promise.resolve(result);
        }
    }
}
