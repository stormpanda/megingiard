package com.stormpanda.megingiard.mirrorserver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.view.Surface;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class DirectSurfaceClient {
    private static final String APP_PACKAGE = "com.stormpanda.megingiard";
    private static final String SERVICE_CLASS = "com.stormpanda.megingiard.mirror.DirectMirrorSurfaceService";
    private static final String DESCRIPTOR = "com.stormpanda.megingiard.mirror.IDirectMirrorSurfaceService";
    private static final int TRANSACTION_ACQUIRE_SURFACE = IBinder.FIRST_CALL_TRANSACTION;

    private DirectSurfaceClient() {}

    static Surface acquireSurface(long timeoutMs) throws Exception {
        Context context = systemContext();
        Intent intent = new Intent().setComponent(new ComponentName(APP_PACKAGE, SERVICE_CLASS));
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> binderRef = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binderRef.set(service);
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                binderRef.set(null);
            }
        };

        boolean bound = context.bindService(intent, Context.BIND_AUTO_CREATE, executor, connection);
        if (!bound) {
            executor.shutdownNow();
            return null;
        }

        try {
            if (!connected.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return null;
            }
            IBinder binder = binderRef.get();
            if (binder == null) return null;

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                if (!binder.transact(TRANSACTION_ACQUIRE_SURFACE, data, reply, 0)) {
                    return null;
                }
                reply.readException();
                if (reply.readInt() == 0) return null;
                return Surface.CREATOR.createFromParcel(reply);
            } finally {
                reply.recycle();
                data.recycle();
            }
        } finally {
            try { context.unbindService(connection); } catch (Throwable ignored) {}
            executor.shutdownNow();
        }
    }

    private static Context systemContext() throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Method systemMain = activityThreadClass.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        Object activityThread = systemMain.invoke(null);
        Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
        getSystemContext.setAccessible(true);
        return (Context) getSystemContext.invoke(activityThread);
    }
}