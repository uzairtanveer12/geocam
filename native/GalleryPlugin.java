package com.geocam.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.os.Build;
import android.util.Range;
import android.util.Rational;
import java.util.Set;
import com.getcapacitor.JSArray;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.InputStream;
import java.io.OutputStream;

@CapacitorPlugin(name = "Gallery")
public class GalleryPlugin extends Plugin {

    private byte[] decode(String data) {
        int i = data.indexOf(",");
        return Base64.decode(i >= 0 ? data.substring(i + 1) : data, Base64.DEFAULT);
    }

    @PluginMethod
    public void save(PluginCall call) {
        String data = call.getString("data");
        String name = call.getString("name", "GeoCam_" + System.currentTimeMillis() + ".jpg");
        if (data == null) {
            call.reject("No image data");
            return;
        }
        byte[] bytes;
        try {
            bytes = decode(data);
        } catch (Throwable e) {
            call.reject("Decode failed: " + e);
            return;
        }
        String[] folders = {
            Environment.DIRECTORY_PICTURES + "/GeoCam",
            Environment.DIRECTORY_DCIM + "/GeoCam"
        };
        StringBuilder err = new StringBuilder();
        for (String folder : folders) {
            try {
                Uri uri = write(bytes, name, folder);
                JSObject ret = new JSObject();
                ret.put("uri", uri.toString());
                ret.put("folder", folder);
                call.resolve(ret);
                return;
            } catch (Throwable e) {
                err.append(folder).append(": ").append(e).append(" | ");
            }
        }
        call.reject(err.toString());
    }

    @PluginMethod
    public void share(final PluginCall call) {
        final String data = call.getString("data");
        final String name = call.getString("name", "GeoCam_" + System.currentTimeMillis() + ".jpg");
        final String text = call.getString("text", "");
        final String old = call.getString("uri", "");
        try {
            Uri uri = null;
            ContentResolver cr = getContext().getContentResolver();
            if (old != null && !old.isEmpty()) {
                try {
                    uri = Uri.parse(old);
                    InputStream is = cr.openInputStream(uri);
                    if (is == null) {
                        uri = null;
                    } else {
                        is.close();
                    }
                } catch (Throwable e) {
                    uri = null;
                }
            }
            if (uri == null) {
                if (data == null) {
                    call.reject("No image data");
                    return;
                }
                uri = write(decode(data), name, Environment.DIRECTORY_PICTURES + "/GeoCam");
            }
            final Uri shareUri = uri;
            getActivity().runOnUiThread(() -> {
                try {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("image/jpeg");
                    send.putExtra(Intent.EXTRA_STREAM, shareUri);
                    if (text != null && !text.isEmpty()) {
                        send.putExtra(Intent.EXTRA_TEXT, text);
                    }
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getActivity().startActivity(Intent.createChooser(send, "Share photo"));
                    JSObject ret = new JSObject();
                    ret.put("uri", shareUri.toString());
                    call.resolve(ret);
                } catch (Throwable e) {
                    call.reject("Share failed: " + e);
                }
            });
        } catch (Throwable e) {
            call.reject("Share failed: " + e);
        }
    }

    @PluginMethod
    public void openUrl(final PluginCall call) {
        final String url = call.getString("url");
        if (url == null) {
            call.reject("No url");
            return;
        }
        getActivity().runOnUiThread(() -> {
            try {
                getActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                call.resolve();
            } catch (Throwable e) {
                call.reject("Open failed: " + e);
            }
        });
    }

    @PluginMethod
    public void info(final PluginCall call) {
        JSObject o = new JSObject();
        try {
            Context c = getContext();
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            o.put("versionName", pi.versionName);
            o.put("versionCode", pi.getLongVersionCode());
            o.put("model", Build.MODEL);
            o.put("manufacturer", Build.MANUFACTURER);
            o.put("android", Build.VERSION.RELEASE);
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("cameraPermission", c.checkSelfPermission(Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED);
            o.put("finePermission", c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED);
            o.put("coarsePermission", c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED);
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            o.put("gpsEnabled", lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
            o.put("networkEnabled", lm != null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        } catch (Throwable e) {
            o.put("error", String.valueOf(e));
        }
        JSArray cams = new JSArray();
        try {
            CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics cc = cm.getCameraCharacteristics(id);
                JSObject co = new JSObject();
                co.put("id", id);
                Integer lf = cc.get(CameraCharacteristics.LENS_FACING);
                co.put("facing", lf == null ? "unknown" : lf == CameraCharacteristics.LENS_FACING_BACK ? "back" : lf == CameraCharacteristics.LENS_FACING_FRONT ? "front" : "external");
                Set<String> phys = cc.getPhysicalCameraIds();
                co.put("physicalLenses", phys.size());
                Range<Float> zr = cc.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                if (zr != null) {
                    co.put("zoomMin", zr.getLower().doubleValue());
                    co.put("zoomMax", zr.getUpper().doubleValue());
                }
                Boolean fl = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                co.put("flash", fl != null && fl);
                Range<Integer> ae = cc.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
                Rational st = cc.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
                if (ae != null) {
                    co.put("aeMin", ae.getLower().intValue());
                    co.put("aeMax", ae.getUpper().intValue());
                }
                if (st != null) {
                    co.put("aeStep", st.doubleValue());
                }
                cams.put(co);
            }
        } catch (Throwable e) {
            o.put("cameraError", String.valueOf(e));
        }
        o.put("cameras", cams);
        call.resolve(o);
    }

    @PluginMethod
    public void saveText(final PluginCall call) {
        String text = call.getString("text", "");
        String name = call.getString("name", "GeoCam_report.txt");
        try {
            ContentResolver cr = getContext().getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GeoCam");
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                call.reject("insert returned null");
                return;
            }
            try (OutputStream os = cr.openOutputStream(uri)) {
                os.write(text.getBytes("UTF-8"));
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            JSObject ret = new JSObject();
            ret.put("uri", uri.toString());
            ret.put("folder", Environment.DIRECTORY_DOWNLOADS + "/GeoCam");
            call.resolve(ret);
        } catch (Throwable e) {
            call.reject("Save failed: " + e);
        }
    }

    @PluginMethod
    public void shareText(final PluginCall call) {
        final String text = call.getString("text", "");
        final String subject = call.getString("subject", "GeoCam");
        final String uriStr = call.getString("uri", "");
        getActivity().runOnUiThread(() -> {
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, subject);
                if (uriStr != null && !uriStr.isEmpty()) {
                    send.putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr));
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    send.putExtra(Intent.EXTRA_TEXT, text);
                }
                getActivity().startActivity(Intent.createChooser(send, "Share report"));
                call.resolve();
            } catch (Throwable e) {
                call.reject("Share failed: " + e);
            }
        });
    }

    @PluginMethod
    public void delete(final PluginCall call) {
        String uri = call.getString("uri", "");
        try {
            int n = getContext().getContentResolver().delete(Uri.parse(uri), null, null);
            JSObject ret = new JSObject();
            ret.put("deleted", n);
            call.resolve(ret);
        } catch (Throwable e) {
            call.reject("Delete failed: " + e);
        }
    }

    private Uri write(byte[] bytes, String name, String folder) throws Exception {
        ContentResolver cr = getContext().getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, folder);
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) {
            throw new Exception("insert returned null");
        }
        try {
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os == null) {
                    throw new Exception("no output stream");
                }
                os.write(bytes);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            return uri;
        } catch (Exception e) {
            cr.delete(uri, null, null);
            throw e;
        }
    }
}
