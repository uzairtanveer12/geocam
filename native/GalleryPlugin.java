package com.geocam.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.OutputStream;

@CapacitorPlugin(name = "Gallery")
public class GalleryPlugin extends Plugin {

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
            int i = data.indexOf(",");
            bytes = Base64.decode(i >= 0 ? data.substring(i + 1) : data, Base64.DEFAULT);
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
