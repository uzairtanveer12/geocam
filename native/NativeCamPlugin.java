package com.geocam.app;

import android.Manifest;
import android.graphics.Color;
import android.graphics.Outline;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExposureState;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.util.concurrent.TimeUnit;

@CapacitorPlugin(
    name = "NativeCam",
    permissions = { @Permission(strings = { Manifest.permission.CAMERA }, alias = "camera") }
)
public class NativeCamPlugin extends Plugin {

    private PreviewView previewView;
    private ProcessCameraProvider provider;
    private ImageCapture imageCapture;
    private Camera camera;

    private void resize(int w, int h) {
        ViewGroup.LayoutParams lp = previewView.getLayoutParams();
        if (lp == null) {
            lp = new ViewGroup.LayoutParams(w, h);
        }
        lp.width = w;
        lp.height = h;
        previewView.setLayoutParams(lp);
    }

    @PluginMethod
    public void start(PluginCall call) {
        if (getPermissionState("camera") != PermissionState.GRANTED) {
            requestPermissionForAlias("camera", call, "permResult");
            return;
        }
        doStart(call);
    }

    @PermissionCallback
    private void permResult(PluginCall call) {
        if (getPermissionState("camera") == PermissionState.GRANTED) {
            doStart(call);
        } else {
            call.reject("Camera permission denied");
        }
    }

    private void doStart(final PluginCall call) {
        final int x = call.getInt("x", 0);
        final int y = call.getInt("y", 0);
        final int w = call.getInt("w", 100);
        final int h = call.getInt("h", 100);
        final int r = call.getInt("r", 0);
        final boolean front = "front".equals(call.getString("facing", "back"));

        getActivity().runOnUiThread(() -> {
            try {
                View web = getBridge().getWebView();
                ViewGroup parent = (ViewGroup) web.getParent();
                parent.setBackgroundColor(Color.parseColor("#05080c"));
                web.setBackgroundColor(Color.TRANSPARENT);

                if (previewView == null) {
                    previewView = new PreviewView(getContext());
                    previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
                    previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
                    previewView.setOutlineProvider(new ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, Outline outline) {
                            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                        }
                    });
                    previewView.setClipToOutline(true);
                    parent.addView(previewView, 0, new ViewGroup.LayoutParams(w, h));
                } else {
                    resize(w, h);
                }
                previewView.setX(x + web.getLeft());
                previewView.setY(y + web.getTop());

                final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(getContext());
                future.addListener(() -> {
                    try {
                        provider = future.get();
                        provider.unbindAll();
                        Preview preview = new Preview.Builder().build();
                        preview.setSurfaceProvider(previewView.getSurfaceProvider());

                        ResolutionSelector sel = new ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(new ResolutionStrategy(new Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                            .build();
                        imageCapture = new ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setResolutionSelector(sel)
                            .build();

                        CameraSelector selector = front ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
                        try {
                            camera = provider.bindToLifecycle((LifecycleOwner) getActivity(), selector, preview, imageCapture);
                        } catch (Throwable first) {
                            provider.unbindAll();
                            preview = new Preview.Builder().build();
                            preview.setSurfaceProvider(previewView.getSurfaceProvider());
                            imageCapture = new ImageCapture.Builder().build();
                            camera = provider.bindToLifecycle((LifecycleOwner) getActivity(), selector, preview, imageCapture);
                        }

                        ZoomState zs = camera.getCameraInfo().getZoomState().getValue();
                        JSObject ret = new JSObject();
                        ret.put("min", zs != null ? zs.getMinZoomRatio() : 1.0);
                        ret.put("max", zs != null ? zs.getMaxZoomRatio() : 1.0);
                        ret.put("zoom", zs != null ? zs.getZoomRatio() : 1.0);
                        ret.put("hasFlash", camera.getCameraInfo().hasFlashUnit());
                        ExposureState es = camera.getCameraInfo().getExposureState();
                        Range<Integer> rg = es.getExposureCompensationRange();
                        ret.put("expMin", rg.getLower().intValue());
                        ret.put("expMax", rg.getUpper().intValue());
                        ret.put("expStep", es.getExposureCompensationStep().doubleValue());
                        call.resolve(ret);
                    } catch (Throwable e) {
                        call.reject("Camera start failed: " + e);
                    }
                }, ContextCompat.getMainExecutor(getContext()));
            } catch (Throwable e) {
                call.reject("Preview failed: " + e);
            }
        });
    }

    @PluginMethod
    public void setZoom(final PluginCall call) {
        final Double ratio = call.getDouble("ratio", 1.0);
        if (camera == null) {
            call.reject("Camera not started");
            return;
        }
        getActivity().runOnUiThread(() -> {
            try {
                camera.getCameraControl().setZoomRatio(ratio.floatValue());
                call.resolve();
            } catch (Throwable e) {
                call.reject("Zoom failed: " + e);
            }
        });
    }

    @PluginMethod
    public void capture(final PluginCall call) {
        if (imageCapture == null) {
            call.reject("Camera not started");
            return;
        }
        final int rot = call.getInt("rotation", 0);
        final File out = new File(getContext().getCacheDir(), "cap_" + System.currentTimeMillis() + ".jpg");
        final ImageCapture.OutputFileOptions opts = new ImageCapture.OutputFileOptions.Builder(out).build();
        getActivity().runOnUiThread(() -> {
            try {
                int sr = rot == 90 ? Surface.ROTATION_90 : rot == 180 ? Surface.ROTATION_180 : rot == 270 ? Surface.ROTATION_270 : Surface.ROTATION_0;
                imageCapture.setTargetRotation(sr);
                imageCapture.takePicture(
                    opts,
                    ContextCompat.getMainExecutor(getContext()),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                            JSObject ret = new JSObject();
                            ret.put("path", out.getAbsolutePath());
                            call.resolve(ret);
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException e) {
                            call.reject("Capture failed: " + e);
                        }
                    }
                );
            } catch (Throwable e) {
                call.reject("Capture failed: " + e);
            }
        });
    }

    @PluginMethod
    public void setFlash(final PluginCall call) {
        final String mode = call.getString("mode", "off");
        getActivity().runOnUiThread(() -> {
            try {
                if (imageCapture != null) {
                    int fm = "on".equals(mode) ? ImageCapture.FLASH_MODE_ON : "auto".equals(mode) ? ImageCapture.FLASH_MODE_AUTO : ImageCapture.FLASH_MODE_OFF;
                    imageCapture.setFlashMode(fm);
                }
                if (camera != null) {
                    camera.getCameraControl().enableTorch("torch".equals(mode));
                }
            } catch (Throwable ignored) {
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void focus(final PluginCall call) {
        final Double x = call.getDouble("x", 0.0);
        final Double y = call.getDouble("y", 0.0);
        getActivity().runOnUiThread(() -> {
            try {
                if (camera != null && previewView != null) {
                    MeteringPoint pt = previewView.getMeteringPointFactory().createPoint(x.floatValue(), y.floatValue());
                    FocusMeteringAction action = new FocusMeteringAction.Builder(pt, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                        .setAutoCancelDuration(4, TimeUnit.SECONDS)
                        .build();
                    camera.getCameraControl().startFocusAndMetering(action);
                }
            } catch (Throwable ignored) {
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void setExposure(final PluginCall call) {
        final int index = call.getInt("index", 0);
        getActivity().runOnUiThread(() -> {
            try {
                if (camera != null) {
                    camera.getCameraControl().setExposureCompensationIndex(index);
                }
            } catch (Throwable ignored) {
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void layout(final PluginCall call) {
        final int x = call.getInt("x", 0);
        final int y = call.getInt("y", 0);
        final int w = call.getInt("w", 100);
        final int h = call.getInt("h", 100);
        getActivity().runOnUiThread(() -> {
            try {
                if (previewView != null) {
                    View web = getBridge().getWebView();
                    resize(w, h);
                    previewView.setX(x + web.getLeft());
                    previewView.setY(y + web.getTop());
                    previewView.requestLayout();
                }
            } catch (Throwable ignored) {
            }
            call.resolve();
        });
    }

    @Override
    protected void handleOnDestroy() {
        try {
            if (provider != null) provider.unbindAll();
        } catch (Throwable ignored) {
        }
    }

    @PluginMethod
    public void stop(final PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                if (provider != null) provider.unbindAll();
                if (previewView != null && previewView.getParent() != null) {
                    ((ViewGroup) previewView.getParent()).removeView(previewView);
                }
            } catch (Throwable ignored) {
            }
            previewView = null;
            imageCapture = null;
            camera = null;
            call.resolve();
        });
    }
}
