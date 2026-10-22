package com.geocam.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
    name = "Gnss",
    permissions = {
        @Permission(
            strings = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION },
            alias = "location"
        )
    }
)
public class GnssPlugin extends Plugin {

    private LocationManager lm;
    private GnssStatus.Callback statusCb;
    private LocationListener gpsListener;
    private LocationListener netListener;

    @PluginMethod
    public void start(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "permResult");
            return;
        }
        doStart(call);
    }

    @PermissionCallback
    private void permResult(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) {
            doStart(call);
        } else {
            call.reject("Location permission denied");
        }
    }

    private LocationListener makeListener(final String provider) {
        return new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location l) {
                JSObject o = new JSObject();
                o.put("lat", l.getLatitude());
                o.put("lon", l.getLongitude());
                o.put("acc", (double) (l.hasAccuracy() ? l.getAccuracy() : 9999f));
                o.put("provider", provider);
                notifyListeners("loc", o);
            }
        };
    }

    @SuppressLint("MissingPermission")
    private void doStart(final PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                stopInternal();
                lm = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
                if (lm == null) {
                    call.reject("No location service");
                    return;
                }
                statusCb = new GnssStatus.Callback() {
                    @Override
                    public void onSatelliteStatusChanged(@NonNull GnssStatus s) {
                        emitStatus(s);
                    }
                };
                lm.registerGnssStatusCallback(ContextCompat.getMainExecutor(getContext()), statusCb);
                if (lm.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
                    gpsListener = makeListener("gps");
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, ContextCompat.getMainExecutor(getContext()), gpsListener);
                }
                if (lm.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
                    netListener = makeListener("network");
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, ContextCompat.getMainExecutor(getContext()), netListener);
                }
                call.resolve();
            } catch (Throwable e) {
                call.reject("GNSS start failed: " + e);
            }
        });
    }

    private void emitStatus(GnssStatus s) {
        int n = s.getSatelliteCount();
        int used = 0;
        int cn0n = 0;
        double cn0sum = 0;
        boolean dual = false;
        int[] vis = new int[8];
        int[] usd = new int[8];
        for (int i = 0; i < n; i++) {
            int c = s.getConstellationType(i);
            if (c < 0 || c > 7) c = 0;
            vis[c]++;
            if (s.usedInFix(i)) {
                usd[c]++;
                used++;
                cn0sum += s.getCn0DbHz(i);
                cn0n++;
            }
            if (s.hasCarrierFrequencyHz(i)) {
                float f = s.getCarrierFrequencyHz(i);
                if (f > 1.17e9f && f < 1.185e9f) dual = true;
            }
        }
        String[] names = { "UNKNOWN", "GPS", "SBAS", "GLONASS", "QZSS", "BEIDOU", "GALILEO", "IRNSS" };
        JSObject c = new JSObject();
        for (int k = 1; k < 8; k++) {
            if (vis[k] > 0) {
                JSObject e = new JSObject();
                e.put("vis", vis[k]);
                e.put("used", usd[k]);
                c.put(names[k], e);
            }
        }
        JSObject o = new JSObject();
        o.put("visible", n);
        o.put("used", used);
        o.put("dual", dual);
        o.put("cn0", cn0n > 0 ? cn0sum / cn0n : 0.0);
        o.put("c", c);
        notifyListeners("gnss", o);
    }

    @SuppressLint("MissingPermission")
    private void stopInternal() {
        try {
            if (lm != null) {
                if (statusCb != null) lm.unregisterGnssStatusCallback(statusCb);
                if (gpsListener != null) lm.removeUpdates(gpsListener);
                if (netListener != null) lm.removeUpdates(netListener);
            }
        } catch (Throwable ignored) {
        }
        statusCb = null;
        gpsListener = null;
        netListener = null;
    }

    @PluginMethod
    public void stop(final PluginCall call) {
        getActivity().runOnUiThread(() -> {
            stopInternal();
            call.resolve();
        });
    }

    @Override
    protected void handleOnDestroy() {
        stopInternal();
    }
}
