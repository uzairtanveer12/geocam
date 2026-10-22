package com.geocam.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(GalleryPlugin.class);
        registerPlugin(NativeCamPlugin.class);
        registerPlugin(GnssPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
