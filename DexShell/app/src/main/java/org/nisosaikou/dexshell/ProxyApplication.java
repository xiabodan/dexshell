package org.nisosaikou.dexshell;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.DexClassLoader;

public class ProxyApplication extends Application {
    final String TAG = "ProxyApplication_Shell";
    // 源APK中启动类
    final String SRC_APP_MAIN_ACTIVITY = "APPLICATION_CLASS_NAME";
    // 源apk dex的绝对路径
    private String mDexAbsolutePath;
    // 用于存放源Apk用到的so文件
    private String mLibAbsolutePath;
    // 源apk
    private String mSrcApkAbsolutePath;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Log.i(TAG, "attachBaseContext app");

        // 获取存储路径
        File odex = this.getDir("payload_odex", MODE_PRIVATE);
        File libs = this.getDir("payload_lib", MODE_PRIVATE);
        mDexAbsolutePath = odex.getAbsolutePath();//用于存放源apk释放出来的dex
        mLibAbsolutePath = libs.getAbsolutePath();//用于存放源Apk用到的so文件
        mSrcApkAbsolutePath = odex.getAbsolutePath() + "/payload.apk";//用于存放解密后的apk
        File srcApkFile = new File(mSrcApkAbsolutePath);
        if (!srcApkFile.exists())
        // 源apk没有被释放出来（第一次加载）
        {
            try {
                srcApkFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 创建了文件，但是这个文件是空的
            // 我们需要附带的apk释放出来
            byte[] shellDexData;
            try {
                shellDexData = getDexFileFromShellApk();
                // 解出原来的apk，解密。复制libs中的文件到当前lib下
                releaseSrcApkAndSrcLibFiles(shellDexData);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 配置动态加载环境
        //反射获取主线程对象，并从中获取所有已加载的package信息，并中找到当前的LoadApk对象的弱引用
        Object currentActivityThread = RefInvoke.invokeStaticMethod("android.app.ActivityThread", "currentActivityThread", new Class[] {}, new Object[] {});
        String packageName = this.getPackageName();
        ArrayMap mPackages = (ArrayMap) RefInvoke.getFieldOjbect("android.app.ActivityThread", currentActivityThread, "mPackages");
        WeakReference weakReference = (WeakReference) mPackages.get(packageName);

        //创建一个新的DexClassLoader用于加载源Apk，
        // 传入apk路径，dex释放路径，so路径，及父节点的DexClassLoader使其遵循双亲委托模型
        DexClassLoader newDexClassLoader = new DexClassLoader(mSrcApkAbsolutePath, mDexAbsolutePath, mLibAbsolutePath, (ClassLoader) RefInvoke.getFieldOjbect("android.app.LoadedApk", weakReference.get(), "mClassLoader"));
        Log.i(TAG, "attachBaseContext weakReference " + weakReference.get());

        //getClassLoader()等同于 (ClassLoader) RefInvoke.getFieldOjbect()
        //但是为了替换掉父节点我们需要通过反射来获取并修改其值
        //将父节点DexClassLoader替换
        RefInvoke.setFieldOjbect("android.app.LoadedApk", "mClassLoader", weakReference.get(), newDexClassLoader);
        // repalceLoadedApkResDir(weakReference.get(), mSrcApkAbsolutePath);
        Log.i(TAG, "attachBaseContext newDexClassLoader " + newDexClassLoader);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate app");
        // 源apk启动类
        String srcAppClassName = "";
        // 原apk所在路径
        try
        {
            ApplicationInfo applicationInfo = this.getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = applicationInfo.metaData;
            if (bundle != null && bundle.containsKey(SRC_APP_MAIN_ACTIVITY)) {
                srcAppClassName = bundle.getString(SRC_APP_MAIN_ACTIVITY);//className 是配置在xml文件中的。
                Log.i(TAG, "在壳的Mainifest.xml中找到 " + SRC_APP_MAIN_ACTIVITY + " 字段信息 " + srcAppClassName);
            }
            else {
                Log.i(TAG, "在壳的Mainifest.xml中找不到"+SRC_APP_MAIN_ACTIVITY+"字段信息。");
                return;
            }
        }
        catch (Exception e)
        {
            Log.i(TAG, "获取不到包管理器。");
        }

        //获取ActivityThread类下AppBindData类的成员属性 LoadedApk info;
        Object currentActivityThread = RefInvoke.invokeStaticMethod("android.app.ActivityThread", "currentActivityThread", new Class[] {}, new Object[] {});
        Object mBoundApplication = RefInvoke.getFieldOjbect("android.app.ActivityThread", currentActivityThread, "mBoundApplication");
        Object loadedApkInfo = RefInvoke.getFieldOjbect("android.app.ActivityThread$AppBindData", mBoundApplication, "info");

        // 将原来的loadedApkInfo置空
        RefInvoke.setFieldOjbect("android.app.LoadedApk", "mApplication", loadedApkInfo, null);

        // 获取壳线程的Application
        Object oldApplication = RefInvoke.getFieldOjbect("android.app.ActivityThread", currentActivityThread, "mInitialApplication");
        ArrayList<Application> mAllApplications = (ArrayList<Application>) RefInvoke.getFieldOjbect("android.app.ActivityThread", currentActivityThread, "mAllApplications");
        mAllApplications.remove(oldApplication);
        // 构造新的Application
        // 1.更新 2处className
        ApplicationInfo appinfo_In_LoadedApk = (ApplicationInfo) RefInvoke.getFieldOjbect("android.app.LoadedApk", loadedApkInfo, "mApplicationInfo");
        ApplicationInfo appinfo_In_AppBindData = (ApplicationInfo) RefInvoke.getFieldOjbect("android.app.ActivityThread$AppBindData", mBoundApplication, "appInfo");
        appinfo_In_LoadedApk.className = srcAppClassName;
        appinfo_In_AppBindData.className = srcAppClassName;

        Log.i(TAG, "onCreate getClassLoader " + getClassLoader());
        // 2.注册application
        Application app = (Application) RefInvoke.invokeMethod("android.app.LoadedApk", "makeApplication", loadedApkInfo, new Class[] { boolean.class, Instrumentation.class }, new Object[] { false, null });

        //替换ActivityThread中的mInitialApplication
        RefInvoke.setFieldOjbect("android.app.ActivityThread", "mInitialApplication", currentActivityThread, app);
        //替换之前的 内容提供者为刚刚注册的app
        ArrayMap mProviderMap = (ArrayMap) RefInvoke.getFieldOjbect("android.app.ActivityThread", currentActivityThread, "mProviderMap");

        Iterator it = mProviderMap.values().iterator();
        while (it.hasNext()) {
            Object providerClientRecord = it.next();
            Object localProvider = RefInvoke.getFieldOjbect("android.app.ActivityThread$ProviderClientRecord", providerClientRecord, "mLocalProvider");
            RefInvoke.setFieldOjbect("android.content.ContentProvider", "mContext", localProvider, app);
        }
        Log.i(TAG, "onCreate app " + app);
        app.onCreate();
        // try {
        //     mergePluginResources(this, getPackageName(), mSrcApkAbsolutePath);
        // } catch (IllegalAccessException e) {
        //     e.printStackTrace();
        // } catch (InstantiationException e) {
        //     e.printStackTrace();
        // } catch (NoSuchMethodException e) {
        //     e.printStackTrace();
        // } catch (InvocationTargetException e) {
        //     e.printStackTrace();
        // } catch (NoSuchFieldException e) {
        //     e.printStackTrace();
        // }
    }

    private static void repalceLoadedApkResDir(Object loadedApk, String dexPath) {
        RefInvoke.setFieldOjbect("android.app.LoadedApk", "mResDir", loadedApk, dexPath);
    }

    private static void mergePluginResources(Application application, String apkName, String dexPath)
            throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException, NoSuchFieldException {
        // 创建一个新的 AssetManager 对象
        AssetManager newAssetManagerObj = AssetManager.class.newInstance();
        Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
        // 塞入原来宿主的资源
        addAssetPath.invoke(newAssetManagerObj, application.getBaseContext().getPackageResourcePath());
        // 塞入插件的资源
        File optDexFile = application.getBaseContext().getFileStreamPath(apkName);
        addAssetPath.invoke(newAssetManagerObj, dexPath);

        Log.i("xiabo", "mergePluginResources " + apkName + ", " + application.getBaseContext().getPackageResourcePath() + ", " + dexPath);

        // 创建一个新的 Resources 对象
        Resources newResourcesObj = new Resources(newAssetManagerObj,
                application.getBaseContext().getResources().getDisplayMetrics(),
                application.getBaseContext().getResources().getConfiguration());

        // 获取 ContextImpl 中的 Resources 类型的 mResources 变量，并替换它的值为新的 Resources 对象
        Field resourcesField = application.getBaseContext().getClass().getDeclaredField("mResources");
        resourcesField.setAccessible(true);
        resourcesField.set(application.getBaseContext(), newResourcesObj);

        // 获取 ContextImpl 中的 LoadedApk 类型的 mPackageInfo 变量
        Field packageInfoField = application.getBaseContext().getClass().getDeclaredField("mPackageInfo");
        packageInfoField.setAccessible(true);
        Object packageInfoObj = packageInfoField.get(application.getBaseContext());

        // 获取 mPackageInfo 变量对象中类的 Resources 类型的 mResources 变量，，并替换它的值为新的 Resources 对象
        // 注意：这是最主要的需要替换的，如果不需要支持插件运行时更新，只留这一个就可以了
        Field resourcesField2 = packageInfoObj.getClass().getDeclaredField("mResources");
        resourcesField2.setAccessible(true);
        resourcesField2.set(packageInfoObj, newResourcesObj);

        // 获取 ContextImpl 中的 Resources.Theme 类型的 mTheme 变量，并至空它
        // 注意：清理mTheme对象，否则通过inflate方式加载资源会报错, 如果是activity动态加载插件，则需要把activity的mTheme对象也设置为null
        Field themeField = application.getBaseContext().getClass().getDeclaredField("mTheme");
        themeField.setAccessible(true);
        themeField.set(application.getBaseContext(), null);
    }

    private byte[] getDexFileFromShellApk() throws IOException {
        ByteArrayOutputStream dexByteArrayOutputStream = new ByteArrayOutputStream();
        ZipInputStream localZipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(this.getApplicationInfo().sourceDir)));
        while (true)
        {
            ZipEntry localZipEntry = localZipInputStream.getNextEntry();
            if (localZipEntry == null)
            {
                localZipInputStream.close();
                break;
            }

            // 拿到dex文件
            if (localZipEntry.getName().equals("classes.dex"))
            {
                byte[] arrayOfByte = new byte[1024];
                while (true)
                {
                    int i = localZipInputStream.read(arrayOfByte);
                    if (i == -1)
                        break;
                    // 写入输出流
                    dexByteArrayOutputStream.write(arrayOfByte, 0, i);
                }
            }
            localZipInputStream.closeEntry();
        }
        localZipInputStream.close();
        return dexByteArrayOutputStream.toByteArray();
    }

    private void releaseSrcApkAndSrcLibFiles(byte[] shellDexData) throws IOException {
        int shellDexDataLength = shellDexData.length;

        //取被加壳apk的长度(长度存储在壳dex的最后四个字节)
        byte[] dexlen = new byte[4];
        System.arraycopy(shellDexData, shellDexDataLength - 4, dexlen, 0, 4);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(dexlen);
        DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);
        int srcDexSize = dataInputStream.readInt();
        //取出apk
        byte[] encryptedSrcApkData = new byte[srcDexSize];
        System.arraycopy(shellDexData, shellDexDataLength - 4 - srcDexSize, encryptedSrcApkData, 0, srcDexSize);

        //对源程序Apk进行解密
        byte[] decryptedArcApkData = decryptSrcApk(encryptedSrcApkData);

        //写入源apk文件
        File file = new File(mSrcApkAbsolutePath);
        try {
            FileOutputStream localFileOutputStream = new FileOutputStream(file);
            localFileOutputStream.write(decryptedArcApkData);
            localFileOutputStream.close();
        } catch (IOException localIOException) {
            throw new RuntimeException(localIOException);
        }

        //分析源apk文件
        ZipInputStream localZipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)));
        while (true)
        {
            ZipEntry srcApk = localZipInputStream.getNextEntry();
            if (srcApk == null) {
                localZipInputStream.close();
                break;
            }

            //依次取出被加壳apk用到的so文件，放到 libPath中（data/data/包名/payload_lib)
            String fileName = srcApk.getName();
            if (fileName.startsWith("lib/") && fileName.endsWith(".so"))
            {
                File fileInSrcLibDir = new File(mLibAbsolutePath + "/" + fileName.substring(fileName.lastIndexOf('/')));
                fileInSrcLibDir.createNewFile();
                FileOutputStream fileOutputStream = new FileOutputStream(fileInSrcLibDir);
                // 复制文件到当前壳程序的lib目录下
                byte[] copyBuffer = new byte[1024];
                while (true)
                {
                    int i = localZipInputStream.read(copyBuffer);
                    if (i == -1)
                        break;
                    fileOutputStream.write(copyBuffer, 0, i);
                }
                fileOutputStream.flush();
                fileOutputStream.close();
            }
            localZipInputStream.closeEntry();
        }
        localZipInputStream.close();
    }

    // 解密
    private byte[] decryptSrcApk(byte[] srcApkData)
    {
        for(int i = 0; i < srcApkData.length; i++)
        {
            srcApkData[i] = (byte)(0xFF ^ srcApkData[i]);
        }
        return srcApkData;
    }
}
