package org.nisosaikou.helloworld;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import org.nisosaikou.dexshell.TestParentClassLoader;

import java.lang.ref.WeakReference;
import java.util.Iterator;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("xiabo", "MainActivity onCreate " + savedInstanceState);
        Button button = findViewById(R.id.button);
        Log.i("xiabo", "MainActivity onCreate button " + button);
        if (button != null) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.i("xiabo", "MainActivity button onClicked");
                }
            });
        }

        // 配置动态加载环境
        //反射获取主线程对象，并从中获取所有已加载的package信息，并中找到当前的LoadApk对象的弱引用
        Object currentActivityThread = RefInvoke.invokeStaticMethod("android.app.ActivityThread", "currentActivityThread", new Class[] {}, new Object[] {});
        String packageName = this.getPackageName();
        ArrayMap mPackages = (ArrayMap) RefInvoke.getFieldOjbect("android.app.ActivityThread", currentActivityThread, "mPackages");
        Iterator<Object> it = mPackages.keySet().iterator();
        while (it.hasNext()) {
            String key = (String) it.next();
            WeakReference weakReference = (WeakReference) mPackages.get(key);
            Log.i("xiabo", "mPackages:" + key + ", " + weakReference.get());
        }

        ArrayMap mResourcePackages = (ArrayMap) RefInvoke.getFieldOjbect("android.app.ActivityThread", currentActivityThread, "mResourcePackages");
        Iterator<Object> it2 = mPackages.keySet().iterator();
        while (it2.hasNext()) {
            String key = (String) it2.next();
            WeakReference weakReference = (WeakReference) mPackages.get(key);
            Log.i("xiabo", "mResourcePackages:" + key + ", " + weakReference.get());
        }

        Button button2 = findViewById(R.id.button2);
        Log.i("xiabo", "MainActivity onCreate button2 " + button2);
        if (button2 != null) {
            button2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.i("xiabo", "MainActivity button2 onClicked");
                    TestParentClassLoader.test1();
                    TestParentClassLoader.test2();
                }
            });
        }

    }

    private void onClick(View view) {
        Button button = findViewById(R.id.button2);
        Log.i("xiabo", "MainActivity onClick button " + button);
        final Uri URI = Uri.parse("content://my.contentprovider.xiabo");
        getContentResolver().call(URI, "MyContentProviderCall", null, new Bundle());
        TestParentClassLoader.test1();
        TestParentClassLoader.test2();
        getDatasync();
    }

    public void getDatasync(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OkHttpClient client = new OkHttpClient();//创建OkHttpClient对象
                    Request request = new Request.Builder()
                            .url("http://www.baidu.com")//请求接口。如果需要传参拼接到接口后面。
                            .build();//创建Request 对象
                    Response response = null;
                    response = client.newCall(request).execute();//得到Response 对象
                    if (response.isSuccessful()) {
                        Log.d("kwwl","response.code()=="+response.code());
                        Log.d("kwwl","response.message()=="+response.message());
                        Log.d("kwwl","res=="+response.body().string());
                        //此时的代码执行在子线程，修改UI的操作请使用handler跳转到UI线程。
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}