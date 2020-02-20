package com.laulee.hotfix;

import android.app.Application;
import android.content.Context;

import java.io.File;

public class MyApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        //这里默认加载本地patch补丁包
        File patchFile = new File("data/data/com.laulee.hotfix/lib.dex");
        HotFix.installPatch(this, patchFile);
    }
}
