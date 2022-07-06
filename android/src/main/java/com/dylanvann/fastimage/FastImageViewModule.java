package com.dylanvann.fastimage;

import java.io.*;
import java.util.HashMap;
import java.util.UUID;

import android.app.Activity;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
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
    private HashMap<UUID, HashMap<String, Integer>> preloads = new HashMap();

    private static final String REACT_CLASS = "FastImageView";

    FastImageViewModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @ReactMethod
    public synchronized void preload(final ReadableArray sources, Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) return;

        UUID preloadKey = UUID.randomUUID();
        System.out.println("Preload " + preloadKey + " started");
        HashMap<String, Integer> preloadInfo = new HashMap();
        preloadInfo.put("finishedCount", 0);
        preloadInfo.put("skippedCount", 0);
        preloadInfo.put("totalCount", sources.size());
        this.preloads.put(preloadKey, preloadInfo);

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < sources.size(); i++) {
                    final ReadableMap source = sources.getMap(i);
                    final FastImageSource imageSource = FastImageViewConverter.getImageSource(activity, source);

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
                                    System.err.println("Preload " + preloadKey + " error:");
                                    e.printStackTrace();
                                    handlePreloadResult(promise, preloadKey, false);
                                    return false;
                                }
                                @Override
                                public boolean onResourceReady(Object resource, Object model, Target target, DataSource dataSource, boolean isFirstResource) {
                                    handlePreloadResult(promise, preloadKey, true);
                                    return true;
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

    // Create a synchronized method to avoid race conditions when incrementing counts across multiple Glide preload processes
    private synchronized void handlePreloadResult(Promise promise, UUID preloadKey, boolean success) {
        HashMap<String, Integer> preloadInfo = this.preloads.get(preloadKey);
        
        if (success) {
            preloadInfo.put("finishedCount", preloadInfo.get("finishedCount") + 1);
        } else {
            preloadInfo.put("skippedCount", preloadInfo.get("skippedCount") + 1);
        }

        boolean preloadDone = preloadInfo.get("finishedCount") + preloadInfo.get("skippedCount") == preloadInfo.get("totalCount");

        if (preloadDone) {
            System.out.println("Preload " + preloadKey + " done: " + preloadInfo.get("finishedCount") + " of " + preloadInfo.get("totalCount") + " images preloaded");
            WritableMap result = Arguments.createMap();
            result.putInt("finishedCount", preloadInfo.get("finishedCount"));
            result.putInt("skippedCount", preloadInfo.get("skippedCount"));
            this.preloads.remove(preloadKey);
            promise.resolve(result);
        } else {
            this.preloads.put(preloadKey, preloadInfo);
        }
    }
}
