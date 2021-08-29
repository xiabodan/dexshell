package org.nisosaikou.helloworld;

import android.app.Activity;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.lang.ref.WeakReference;
import java.util.Iterator;

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
            Log.i("xiabo", key + ", " + weakReference.get());
        }

        Button button2 = findViewById(R.id.button2);
        Log.i("xiabo", "MainActivity onCreate button2 " + button2);
        if (button2 != null) {
            button2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.i("xiabo", "MainActivity button2 onClicked");
                }
            });
        }

    }

    private void onClick(View view) {
        Button button = findViewById(R.id.button);
        Log.i("xiabo", "MainActivity onClick button " + button);
    }
}