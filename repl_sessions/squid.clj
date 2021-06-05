(ns squid
  (:require [clojure.java.io :as io])
  (:import
   ;; gdx application/native stuff
   com.badlogic.gdx.ApplicationAdapter
   com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
   com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

   ;; gdx generic stuff
   com.badlogic.gdx.Gdx
   com.badlogic.gdx.files.FileHandle 
   com.badlogic.gdx.graphics.Camera
   com.badlogic.gdx.graphics.GL20
   com.badlogic.gdx.graphics.OrthographicCamera
   com.badlogic.gdx.graphics.Texture
   com.badlogic.gdx.graphics.g2d.Batch 
   com.badlogic.gdx.graphics.g2d.SpriteBatch 
   com.badlogic.gdx.math.Rectangle
   com.badlogic.gdx.scenes.scene2d.Actor
   com.badlogic.gdx.scenes.scene2d.Stage
   com.badlogic.gdx.utils.Disposable
   com.badlogic.gdx.utils.Scaling
   com.badlogic.gdx.utils.ScreenUtils
   com.badlogic.gdx.utils.viewport.FillViewport 
   com.badlogic.gdx.utils.viewport.Viewport 
   com.badlogic.gdx.utils.viewport.StretchViewport
   com.badlogic.gdx.utils.viewport.ScalingViewport
   com.badlogic.gdx.InputAdapter
   com.badlogic.gdx.InputProcessor
   com.badlogic.gdx.InputMultiplexer

   ;; squidlib gdx extensions
   squidpony.squidgrid.gui.gdx.FilterBatch
   squidpony.squidgrid.gui.gdx.TextCellFactory
   squidpony.squidgrid.gui.gdx.TextCellFactory$Glyph
   squidpony.squidgrid.gui.gdx.SparseLayers
   squidpony.squidgrid.gui.gdx.SColor
   squidpony.squidgrid.gui.gdx.SquidInput
   squidpony.squidgrid.gui.gdx.SquidMouse
   squidpony.squidgrid.gui.gdx.SquidInput$KeyHandler

   ;; squidlib pure logic
   squidpony.squidmath.GWTRNG
   squidpony.squidgrid.mapping.DungeonGenerator
   squidpony.squidgrid.mapping.DungeonUtility))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ _ t]
     (println t)
     (run! println (.getStackTrace t)))))

(def settings {:seed "apples"
               :grid-width 90
               :grid-height 25
               :cell-width 10
               :cell-height 15})

(def registry (atom {}))
(defn reg! [k v] (swap! registry assoc k v) v)
(defn rget [k] (get @registry k))

(defn input-processor ^InputProcessor []
  (let [{:keys [grid-width grid-height cell-width cell-height]} settings]
    (SquidInput.
     (reify SquidInput$KeyHandler
       (handle [​this key alt ctrl shift]
         (prn key)))
     (SquidMouse.
      cell-width
      cell-height
      grid-width
      grid-height
      0
      0
      (reify InputProcessor
	(keyDown [this keycode] false)
	(keyUp [this keycode] false)
	(keyTyped [this character] false)
	(touchDown [this screenX screenY pointer button] false)
	(touchUp [this screenX screenY pointer button] false)
	(touchDragged [this screenX screenY pointer] false)
	(mouseMoved [this screenX screenY] false)
	(scrolled [this amountX amountY] false)
        )))))

(defn create []
  (reg! :drop-img (Texture. (.internal (Gdx/files) "drop.png")))
  (reg! :camera (doto (OrthographicCamera.)
                  (.setToOrtho false
                               (.getWidth Gdx/graphics)
                               (.getHeight Gdx/graphics))))
  (reg! :batch (FilterBatch.))
  (reg! :viewport (ScalingViewport. Scaling/stretch
                                    (.getWidth Gdx/graphics)
                                    (.getHeight Gdx/graphics)
                                    (rget :camera)))

  (reg! :stage (Stage. (rget :viewport) (rget :batch)))

  (reg! :layers (let [{:keys [grid-width grid-height cell-width cell-height]}
                      settings]
                  (SparseLayers. grid-width grid-height cell-width cell-height)))
  (reg! :squid-input (input-processor))

  (.setInputProcessor Gdx/input ^InputProcessor (rget :squid-input))
  #_
  (InputMultiplexer.
   (into-array InputProcessor [(rget :stage) (input-processor)]))
  (reg! :rng (GWTRNG. ^String (:seed settings)))
  (reg! :dungeon-gen (DungeonGenerator. (:grid-width settings)
                                        (:grid-height settings)
                                        (rget :rng)))

  (reg! :dungeon (DungeonUtility/hashesToLines (.generate ^DungeonGenerator (rget :dungeon-gen))))
  
  (let [{:keys [grid-width grid-height]} settings
        {:keys [^Stage stage ^SparseLayers layers dungeon]} @registry]

    (dotimes [x grid-width]
      (dotimes [y grid-height]
        (.put layers x y (aget ^"[C" (aget ^"[[C" dungeon x) y))))

    (.put layers 5 6 \@ SColor/SAFETY_ORANGE)

    (.addActor stage layers)))

(defn render []
  (try
    (let [{:keys [^Camera camera
                  ^Batch batch
                  ^Texture drop-img
                  ^Stage stage
                  ^SparseLayers layers
                  ^SquidInput squid-input]}
          @registry]
      (.glClearColor Gdx/gl 1 1 0.95 1)
      (.glClear Gdx/gl GL20/GL_COLOR_BUFFER_BIT)

      (when (.hasNext squid-input)
        (.next squid-input))
      
      (.draw stage))
    (catch Exception e
      (prn e))))

(defn resize [width height]
  (.update ^Viewport (rget :viewport) width height true))

(defn start! []
  (reg!
   :application-loop
   (future
     (Lwjgl3Application.
      (proxy [ApplicationAdapter] []
        (create [] (create))
        (render [] (try (render) (catch Exception e (println e))))
        (resize [width height] (resize width height))
        (dispose []
          (doseq [[k v] @registry]
            (when (instance? Disposable v)
              (println "Disposing" k)
              (.dispose ^Disposable v)))))
      (doto (Lwjgl3ApplicationConfiguration.)
        (.setTitle "volta do mar")
        (.setWindowedMode 800 480))))))

(defn cleanup! []
  (.exit Gdx/app)
  (reset! registry {}))

(comment
  (cleanup!))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn tap-cc> [cc]
  (tap> (apply map (comp (partial apply str) vector) cc)))

(comment
  (tap-cc> (DungeonUtility/hashesToLines (.generate dungeon-gen))))
