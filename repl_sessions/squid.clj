(ns squid
  (:import
   ;; gdx application/native stuff
   com.badlogic.gdx.ApplicationAdapter
   com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
   com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

   ;; gdx generic stuff
   com.badlogic.gdx.Gdx;
   com.badlogic.gdx.utils.viewport.FillViewport 
   com.badlogic.gdx.scenes.scene2d.Stage
   com.badlogic.gdx.graphics.g2d.SpriteBatch 
   com.badlogic.gdx.graphics.Texture

   ;; squidlib gdx extensions
   squidpony.squidgrid.gui.gdx.FilterBatch
   squidpony.squidgrid.gui.gdx.TextCellFactory
   squidpony.squidgrid.gui.gdx.TextCellFactory$Glyph
   squidpony.squidgrid.gui.gdx.SparseLayers
   squidpony.squidgrid.gui.gdx.SColor
   
   ;; squidlib pure logic
   squidpony.squidmath.GWTRNG
   squidpony.squidgrid.mapping.DungeonGenerator
   squidpony.squidgrid.mapping.DungeonUtility))



(def settings {:seed "apples"
               :grid-width 90
               :grid-height 25
               :cell-width 10
               :cell-height 15})

(def rng (GWTRNG. (:seed settings)))
(def dungeon-gen (DungeonGenerator. (:grid-width settings) (:grid-height settings) rng))
(def viewport
  (let [{:keys [grid-width grid-height cell-width cell-height]} settings]
    (FillViewport. (* grid-width cell-width)
                   (* grid-height cell-height))))
(defn create []
  (def batch (FilterBatch.) #_(proxy [SpriteBatch] []
                                (begin []
                                  (println ">>>>>>>>>>>>>>BEGIN>>>>>>>>>>>>>>>>>>>")
                                  (run! println (.getStackTrace (Exception.)))
                                  )
                                (end []
                                  (println ">>>>>>>>>>>>>>BEGIN>>>>>>>>>>>>>>>>>>>")
                                  (run! println (.getStackTrace (Exception.)))
                                  )))
  
  (def stage (Stage. viewport batch))
  (def camera (.getCamera stage))
  (def display (let [{:keys [grid-width grid-height cell-width cell-height]} settings]
                 (SparseLayers. grid-width grid-height cell-width cell-height)))
  (.addActor stage display))



(defn render []
  (try
    (doseq [x (range 100)
            y (range 100)]
      (.glyph display \@ SColor/SAFETY_ORANGE x y))
    (.act stage)
    (.apply viewport true)
    (.setProjectionMatrix batch (.-combined camera))
    (.begin batch)
    (.draw (.getRoot stage) batch 1)
    ;;    (.draw (.-font display) batch (str (.getFramesPerSecond Gdx/graphics) " FPS") 100 100)
    (.end batch)
    (catch Exception e
      (println e))))

(defn resize [width height]
  (.update viewport width height true))

(future
  (Lwjgl3Application.
   (proxy [ApplicationAdapter] []
     (create [] (create))
     (render [] (render))
     (resize [width height] (resize width height)))
   (doto (Lwjgl3ApplicationConfiguration.)
     (.setTitle "volta do mar")
     (.setWindowedMode 640 480))))



(defn tap-cc> [cc]
  (tap> (apply map (comp (partial apply str) vector) cc)))

(comment
  (tap-cc> (DungeonUtility/hashesToLines (.generate dungeon-gen))))
