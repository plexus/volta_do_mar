(ns com.github.plexus.desktop
  (:require [com.github.plexus.volta-do-mar :refer [new-gdx-app reg!]])
  (:import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
           com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration))

(defn start! []
  (reg!
   :application-loop
   (future
     (Lwjgl3Application.
      (new-gdx-app)
      (doto (Lwjgl3ApplicationConfiguration.)
        (.setTitle "volta do mar")
        (.setWindowedMode 800 480))))))
