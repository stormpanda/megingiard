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
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class DirectSurfaceClient {
    private static final String TAG = "DirectSurfaceClient";
    private static final String APP_PACKAGE = "com.stormpanda.megingiard";
    private static final String SERVICE_CLASS = "com.stormpanda.megingiard.mirror.DirectMirrorSurfaceService";
    private static final String DESCRIPTOR = "com.stormpanda.megingiard.mirror.IDirectMirrorSurfaceService";
    private static final int TRANSACTION_ACQUIRE_SURFACE = IBinder.FIRST_CALL_TRANSACTION;
    private static final long SYSTEM_CONTEXT_TIMEOUT_MS = 2_000L;

    private DirectSurfaceClient() {}

    static Surface acquireSurface(long timeoutMs) throws Exception {
        System.err.println(TAG + ": acquireSurface() starting with timeout=" + timeoutMs + "ms");
        Context context = null;
        try {
            context = systemContext();
            System.err.println(TAG + ": acquired system context");
        } catch (Exception e) {
            System.err.println(TAG + ": systemContext() failed: " + e);
            e.printStackTrace();
            return null;
        }

        if (context == null) {
            System.err.println(TAG + ": systemContext returned null");
            return null;
        }

        Intent intent = new Intent().setComponent(new ComponentName(APP_PACKAGE, SERVICE_CLASS));
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> binderRef = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                System.err.println(TAG + ": onServiceConnected: " + name);
                binderRef.set(service);
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                System.err.println(TAG + ": onServiceDisconnected: " + name);
                binderRef.set(null);
            }
        };

        boolean bound = false;
        try {
            System.err.println(TAG + ": calling bindService for " + SERVICE_CLASS);
            bound = context.bindService(intent, Context.BIND_AUTO_CREATE, executor, connection);
            System.err.println(TAG + ": bindService returned " + bound);
        } catch (Exception e) {
            System.err.println(TAG + ": bindService threw exception: " + e);
            e.printStackTrace();
        }

        if (!bound) {
            System.err.println(TAG + ": bindService returned false; service not found or permission denied");
            executor.shutdownNow();
            return null;
        }

        try {
            System.err.println(TAG + ": waiting for service connection (timeout=" + timeoutMs + "ms)");
            boolean connected_ok = connected.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!connected_ok) {
                System.err.println(TAG + ": timed out waiting for service connection");
                return null;
            }
            IBinder binder = binderRef.get();
            if (binder == null) {
                System.err.println(TAG + ": binder is null after connection");
                return null;
            }

            System.err.println(TAG + ": executing transact to acquire surface");
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                boolean transact_ok = binder.transact(TRANSACTION_ACQUIRE_SURFACE, data, reply, 0);
                if (!transact_ok) {
                    System.err.println(TAG + ": transact returned false");
                    return null;
                }
                reply.readException();
                int hasValue = reply.readInt();
                System.err.println(TAG + ": transact returned hasValue=" + hasValue);
                if (hasValue == 0) {
                    System.err.println(TAG + ": service returned no surface (hasValue=0)");
                    return null;
                }
                Surface surface = Surface.CREATOR.createFromParcel(reply);
                System.err.println(TAG + ": successfully acquired surface: " + surface);
                return surface;
            } finally {
                reply.recycle();
                data.recycle();
            }
        } catch (Exception e) {
            System.err.println(TAG + ": exception during connection/transact: " + e);
            e.printStackTrace();
            return null;
        } finally {
            try { context.unbindService(connection); } catch (Throwable ignored) {}
            executor.shutdownNow();
        }
    }

    private static Context systemContext() throws Exception {
        System.err.println(TAG + ": systemContext() acquiring via ActivityThread reflection");
        FutureTask<Context> task = new FutureTask<>(() -> {
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Method systemMain = activityThreadClass.getDeclaredMethod("systemMain");
                systemMain.setAccessible(true);
                System.err.println(TAG + ": calling ActivityThread.systemMain()");
                Object activityThread = systemMain.invoke(null);
                System.err.println(TAG + ": got ActivityThread instance");
                Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
                getSystemContext.setAccessible(true);
                Context ctx = (Context) getSystemContext.invoke(activityThread);
                System.err.println(TAG + ": got system context: " + ctx);
                return ctx;
            } catch (Exception e) {
                System.err.println(TAG + ": systemContext reflection failed: " + e);
                e.printStackTrace();
                throw e;
            }
        });
        Thread thread = new Thread(task, "DirectSurfaceSystemContext");
        thread.start();
        try {
            Context ctx = task.get(SYSTEM_CONTEXT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            System.err.println(TAG + ": systemContext() returning: " + ctx);
            return ctx;
        } catch (Exception e) {
            System.err.println(TAG + ": systemContext() timed out or failed: " + e);
            throw e;
        }
    }
}
