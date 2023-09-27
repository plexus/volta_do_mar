# libdgx Android with Clojure

## Compiling a compatible Clojure

Compiling this required a custom build of Clojure, and I seem to have lost the
changes I had made, but I think it's this block in Reflector.java that is
causing issues.

``` java
static {
	MethodHandle pred = null;
	try {
		if (! isJava8())
			pred = MethodHandles.lookup().findVirtual(Method.class, "canAccess", MethodType.methodType(boolean.class, Object.class));
	} catch (Throwable t) {
		Util.sneakyThrow(t);
	}
	CAN_ACCESS_PRED = pred;
}
```

The tl;dr is this
- Android's JVM (Dalvik) uses a different binary format for representing compiled artifacts, it does not use `.class` files or standard JVM bytecode
- It is however able to turn compiled class files (from Java or Clojure source files) into Dalvik-compatible output
- Clojure's compiler generates bytecode at runtime and injects it into the running JVM
- This will not fly on Dalvik
- We can sidestep the problem by making sure everything is AOT compiled beforehand, so we don't need the compiler or reflector at runtime

## Building

The general approach is that we first AOT compile the clojure code, this creates
a jar, which the libgdx android build can then pick up, so we have a "double
build", we first uberjar the Clojure portion, then add that into our libgdx
project.

#+begin_src shell
clj -X:uberjar
#+end_src

### Starting over 2022-08-04, New Castle, VA

#### gdx-setup

Let's try to figure out how to do a libgdx android project... and this time take some notes

Download gdx-setup.jar from https://libgdx.com/wiki/start/project-generation

You can run this with `java -jar gdx-setup.jar` and it'll give you a GUI to
generate a project, or you can run it with CLI flags: https://libgdx.com/wiki/start/project-setup-via-command-line

```shell
java -jar gdx-setup.jar --dir mygame --name mygame --package com.badlogic.mygame --mainClass MyGame --sdkLocation mySdkLocation [--excludeModules <modules>] [--extensions <extensions>]
```

```
➜ java -jar gdx-setup.jar --dir mygame --name mygame --package com.badlogic.mygame --mainClass MyGame --sdkLocation ~/Android/Sdk --excludeModules ios,html
```

Next step is running the android emulator, or connecting a device so adb can find it, then you can 

```
./gradlew android:installDebug
```

to get it onto the device, and


```
./gradlew android:run
```

to start it up

#### minSdkLevel

To prevent complaints about MethodHandle.invoke, bump the minimum Android sdk
level to 26. Find the android block in `android/build.gradle`

```
android {
    ...
    defaultConfig {
        ...
        minSdkVersion 26
    }
}
```

#### Installing the necessary Android stuff

Not sure if this will be exhaustive, since I already seem to have some Android
stuff installed, but I'll try to do this step by step.

Download "Command Line Tools Only" for Android https://developer.android.com/studio#downloads
-> commandlinetools-linux-8512546_latest.zip 

This contains a directory cmdline-tools, which seems to very strongly insist
that it be placed under ~<sdk-path>/cmdline-tools/latest~

#+begin_src 
unzip ~/Downloads/commandlinetools-linux-8512546_latest.zip
mkdir -p ~/Android/Sdk/cmdline-tools
mv cmdline-tools ~/Android/Sdk/cmdline-tools/latest
#+end_src

#+begin_src shell
if [[ -d "$HOME/Android" ]]; then
    export ANDROID_HOME=$HOME/Android/Sdk
    export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin:$ANDROID_HOME/platform-tools
fi
#+end_src

This provides sdkmanager:
https://developer.android.com/studio/command-line/sdkmanager

You can use that to install sdk images and emulators

https://gist.github.com/mrk-han/66ac1a724456cadf1c93f4218c6060ae

Based on output from gdx-setup it seems to prefer Android SDK version 31, so we'll stick to that.

```shell
sdkmanager --list | grep system-images | grep 31
sdkmanager --install 'system-images;android-31;default;x86_64'
```

Now we can create a virtual device, and run an emulator

```shell
avdmanager create avd --name android-31 --package 'system-images;android-31;default;x86_64'
emulator -avd android-31 
```

Seems it might also be good to intall ndk (android native code extension tooling)

```
sdkmanager ndk-bundle
```

Since we're getting these warnings from Gradle

```
> Task :android:stripDebugDebugSymbols
Unable to strip the following libraries, packaging them as they are: libgdx.so.

> Task :android:stripReleaseDebugSymbols
Unable to strip the following libraries, packaging them as they are: libgdx.so.

> Task :android:extractReleaseNativeSymbolTables
Unable to extract native debug metadata from /home/arne/clj-projects/gdx-reprise/gdx-app/android/build/intermediates/merged_native_libs/release/out/lib/arm64-v8a/libgdx.so because unable to locate the objcopy executable for the arm64-v8a ABI.
Unable to extract native debug metadata from /home/arne/clj-projects/gdx-reprise/gdx-app/android/build/intermediates/merged_native_libs/release/out/lib/x86/libgdx.so because unable to locate the objcopy executable for the x86 ABI.
Unable to extract native debug metadata from /home/arne/clj-projects/gdx-reprise/gdx-app/android/build/intermediates/merged_native_libs/release/out/lib/armeabi-v7a/libgdx.so because unable to locate the objcopy executable for the armeabi-v7a ABI.
Unable to extract native debug metadata from /home/arne/clj-projects/gdx-reprise/gdx-app/android/build/intermediates/merged_native_libs/release/out/lib/x86_64/libgdx.so because unable to locate the objcopy executable for the x86_64 ABI.
```

### Compiling and Running the libgdx app

In the generated project directory you can do `./gradlew tasks` to see a list of
gradle tasks, and `./gradlew :desktop:run` to run the desktop version.

For the android version `./gradlew :android:run` will complain that it can't
find the Activity class, seems `run` in this case does not imply `build`.

Seems the magic incantation is `installDebug`, make sure the `emulator` is
running, see above.

```
./gradlew android:installDebug
```

And then you can

```
./gradelw android:run
```

Which just runs

```
adb shell am start -n 'package-name/activity-name`
```

You can interrupt it with

```
adb shell am force-stop 'package-name/activity-name'
```

### Cleaning up after Gradle

A `rm -rf ~/.gradle/caches` or even `rm -rf ~/.gradle` seems to do wonders when
things start misbehaving.

# Cheat Sheet

```
emulator -avd android-31
cd ~/clj-projects/volta_do_mar && clj -X:uberjar
cd ~/clj-projects/gdx-reprise/mygame && ./gradlew android:installDebug
# adb shell am force-stop 'com.plexus.hellogdx/com.plexus.hellogdx.AndroidLauncher'
# adb shell am start -n 'com.plexus.hellogdx/com.plexus.hellogdx.AndroidLauncher'
adb shell pm clear com.badlogic.mygame
adb shell am start -n com.badlogic.mygame/.AndroidLauncher
```

cd ~/clj-projects/volta_do_mar && clj -X:uberjar && cd ~/clj-projects/gdx-reprise/mygame && ./gradlew android:installDebug && adb shell pm clear com.badlogic.mygame && adb shell am start -n com.badlogic.mygame/.AndroidLauncher


# Links

- https://github.com/simpligility/maven-android-sdk-deployer
