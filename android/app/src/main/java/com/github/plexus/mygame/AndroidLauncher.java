package com.github.plexus.mygame;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.ApplicationListener;
import clojure.java.api.Clojure;
import clojure.lang.IFn;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("com.github.plexus.volta-do-mar"));
        IFn newGdxApp = Clojure.var("com.github.plexus.volta-do-mar", "new-gdx-app");
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        initialize((ApplicationListener)newGdxApp.invoke(), config);
    }
}
