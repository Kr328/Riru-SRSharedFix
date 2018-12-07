package com.github.kr328.srsf;

import android.app.IActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;

@SuppressWarnings("unused")
public class Injector {
    public static IBinder getContextObjectReplaced() {
        //Log.i(Global.TAG ,"Java getContextObject called");

        return LocalInterfaceProxy.createInterfaceProxyBinder(ServiceManagerNative.asInterface(getContextObjectOriginal()) ,IServiceManager.class.getName() ,(original , replaced , method , args) -> {
            if ( "getService".equals(method.getName()) ) {
                switch ( args[0].toString() ) {
                    case Context.ACTIVITY_SERVICE :
                        return LocalInterfaceProxy.createInterfaceProxyBinder(IActivityManager.Stub.asInterface(original.getService(Context.ACTIVITY_SERVICE)) ,IActivityManager.class.getName() ,Injector::onActivityServiceCalled);
                }
            }
            return method.invoke(original ,args);
        });
    }

    private static Object onActivityServiceCalled(IActivityManager original ,IActivityManager replaced ,Method method ,Object[] args) throws Throwable {
        switch ( method.getName() ) {
            case "startActivity" :
                Intent intent = (Intent) args[2];
                args[2] = onStartActivity((String) args[1] ,intent);
                break;
        }

        return method.invoke(original ,args);
    }

    private static Intent onStartActivity(String sourcePackage ,Intent intent) {
        Uri uri;

        if ( intent.getAction() == null || sourcePackage == null )
            return intent;

        switch ( intent.getAction() ) {
            case Intent.ACTION_VIEW :
                uri = intent.getData();
                if ( uri != null && uri.getScheme().equals("file") )
                    intent.setData(uri = Uri.fromFile(new File(toRedirectedPath(sourcePackage ,uri.getPath()))));
                Log.i(Global.TAG ,"Try fix " + (uri == null ? "null" : uri));
                break;
            case Intent.ACTION_SEND :
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if ( uri != null && uri.getScheme().equals("file") )
                    intent.putExtra(Intent.EXTRA_STREAM ,uri = Uri.fromFile(new File(toRedirectedPath(sourcePackage ,uri.getPath()))));
                Log.i(Global.TAG ,"Try fix " + (uri == null ? "null" : uri));
                break;
            case Intent.ACTION_SEND_MULTIPLE :
                ArrayList<Parcelable> listExtra = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if ( listExtra == null )
                    break;
                ArrayList<Parcelable> ouputListExtra = new ArrayList<>();
                for ( Parcelable p : listExtra ) {
                  if ( p instanceof Uri && ((Uri)p).getScheme().equals("file"))
                      ouputListExtra.add(Uri.fromFile(new File(toRedirectedPath(sourcePackage ,((Uri)p).getPath()))));
                  else
                      ouputListExtra.add(p);
                }
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM ,ouputListExtra);
                Log.i(Global.TAG ,"Try fix " + ouputListExtra);
                break;
        }

        return intent;
    }

    private static String toRedirectedPath(String packageName ,String path) {
        File sourceFile = new File(path);
        String externalStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String sourcePath = sourceFile.getAbsolutePath();
        String result = path;

        if ( sourcePath.startsWith(externalStoragePath) ) {
            String targetPath;
            targetPath = sourcePath.replace(externalStoragePath ,externalStoragePath + "/Android/data/" + packageName + "/cache/sdcard/");
            if ( new File(targetPath).exists() )
                result = targetPath;
            targetPath = sourcePath.replace(externalStoragePath ,externalStoragePath + "/Android/data/" + packageName + "/sdcard/");
            if ( new File(targetPath).exists() )
                result = targetPath;
        }

        Log.i(Global.TAG ,sourcePath + " => " + result);

        return result;
    }

    public static native IBinder getContextObjectOriginal();
}
