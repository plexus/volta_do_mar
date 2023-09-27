(ns com.github.plexus.volta-do-mar
  (:require [clojure.java.io :as io])
  (:import
   ;; gdx application/native stuff
   com.badlogic.gdx.ApplicationAdapter
   com.badlogic.gdx.ApplicationListener

   ;; gdx generic stuff
   com.badlogic.gdx.Gdx
   com.badlogic.gdx.files.FileHandle
   com.badlogic.gdx.graphics.Camera
   com.badlogic.gdx.graphics.Color
   com.badlogic.gdx.graphics.GL20
   com.badlogic.gdx.graphics.OrthographicCamera
   com.badlogic.gdx.graphics.Texture
   com.badlogic.gdx.graphics.g2d.Batch
   com.badlogic.gdx.graphics.g2d.SpriteBatch
   com.badlogic.gdx.math.Rectangle
   com.badlogic.gdx.scenes.scene2d.Actor
   com.badlogic.gdx.scenes.scene2d.Stage
   com.badlogic.gdx.utils.NumberUtils
   com.badlogic.gdx.utils.Disposable
   com.badlogic.gdx.utils.Scaling
   com.badlogic.gdx.utils.ScreenUtils
   com.badlogic.gdx.utils.viewport.FillViewport
   com.badlogic.gdx.utils.viewport.Viewport
   com.badlogic.gdx.utils.viewport.StretchViewport
   com.badlogic.gdx.utils.viewport.ScalingViewport
   com.badlogic.gdx.utils.viewport.ScreenViewport
   com.badlogic.gdx.InputAdapter
   com.badlogic.gdx.InputProcessor
   com.badlogic.gdx.InputMultiplexer
   com.badlogic.gdx.math.Interpolation

   ;; squidlib gdx extensions
   squidpony.squidgrid.gui.gdx.DefaultResources
   squidpony.squidgrid.gui.gdx.FilterBatch
   squidpony.squidgrid.gui.gdx.TextCellFactory
   squidpony.squidgrid.gui.gdx.TextCellFactory$Glyph
   squidpony.squidgrid.gui.gdx.SparseLayers
   squidpony.squidgrid.gui.gdx.SColor
   squidpony.squidgrid.gui.gdx.SquidInput
   squidpony.squidgrid.gui.gdx.SquidMouse
   squidpony.squidgrid.gui.gdx.SquidInput$KeyHandler

   ;; squidlib pure logic
   squidpony.squidmath.Coord
   squidpony.squidmath.IRNG
   squidpony.squidmath.GWTRNG
   squidpony.squidmath.GreasedRegion
   squidpony.squidgrid.Radius
   squidpony.squidgrid.FOV
   squidpony.squidgrid.mapping.LineKit
   squidpony.squidgrid.mapping.DungeonGenerator
   squidpony.squidgrid.mapping.DungeonUtility))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ _ t]
     (println t)
     (run! println (.getStackTrace t)))))

(defprotocol Tweenable
  (^double << [this] "Get current value")
  (>> [thix value] "Set new value"))

(def registry (atom {}))
(defn reg! [k v] (swap! registry assoc k v) v)
(defn rget [k] (get @registry k))

(defonce registry-types (atom '{seed String
                                rng IRNG

                                grid-height double
                                grid-width double
                                cell-height double
                                cell-width double
                                player-x long
                                player-y long
                                stage Stage
                                viewport Viewport
                                camera OrthographicCamera
                                batch Batch

                                dungeon-gen DungeonGenerator
                                dungeon "[[C"
                                line-dungeon "[[C"
                                layers SparseLayers
                                visible "[[D"
                                resistance "[[D"

                                player-coord Coord
                                player-glyph TextCellFactory$Glyph
                                player-speed double
                                player-tween Interpolation
                                squid-input SquidInput

                                seen GreasedRegion
                                blockage GreasedRegion
                                currently-seen GreasedRegion
                                }))

(defmacro register [& bindings]
  (doseq [[sym form] (partition 2 bindings)
          :let [tag (:tag (meta sym))]
          :when (and (not= '_ sym) tag)]
    (swap! registry-types assoc sym tag))
  (cons 'let
        [(into []
               cat
               (for [[sym form] (partition 2 bindings)]
                 [sym `(reg! ~(keyword (str sym)) ~form)]))]))

(defmacro let-reg [bindings & body]
  (cons 'let
        (into [(into []
                     cat
                     (for [sym bindings]
                       [(with-meta sym {:tag (get @registry-types sym)})
                        `(rget ~(keyword (str sym)))]))]
              body)))

(defn aget-cc [cc x y] (aget ^"[C" (aget ^"[[C" cc x) y))
(defn aget-dd ^double [dd x y] (aget ^"[D" (aget ^"[[D" dd x) y))

;; (.a (.fromHsv (Color.) 320 1 0.44))
;; (.set (Color.) (NumberUtils/floatToIntColor 6.795984E-39))
(defn tween! [key ^double target speed tween-fn]
  (let-reg [tweens tweenables]
    (let [t (get tweenables key)]
      (swap! tweens assoc key [0 (<< t) target speed tween-fn]))))

(defn smoother-tween
  "Interpolation/smoother"
  ^double [^double a]
  (* a a (- 3 (* a 2)))
  #_(* a a a (+ 10 (* a (- (* a 6) 15)))))

(defn valid-player-pos? [x y]
  (let-reg [dungeon grid-width grid-height]
    (and (< -1 x grid-width)
         (< -1 y grid-height)
         (not= \# (aget-cc dungeon x y)))))

(defn move-player [^double xoff ^double yoff]
  (let-reg [player-glyph player-x player-y cell-height cell-width
            resistance visible blockage seen currently-seen player-speed layers]
    (let [x (+ player-x xoff)
          y (+ player-y yoff)]
      (when (valid-player-pos? x y)
        (when (not= x player-x)
          (tween! ::player-x (.worldX layers x) player-speed identity))
        (when (not= y player-y)
          (tween! ::player-y (.worldY layers y) player-speed identity))
        (swap! registry assoc :player-x x :player-y y)
        (FOV/reuseFOV resistance visible x y 9 Radius/CIRCLE)
        (.refill blockage visible 0.0)
        (.or seen (.remake currently-seen (.not blockage)))
        (.fringe8way blockage)
        ))))

(defn handle-key [k alt ctrl shift]
  (let-reg [camera
            player-glyph player-speed
            cell-width cell-height]
    (case k
      \i (tween! ::zoom (- (.-zoom camera) 0.1) 10 identity)
      \k (tween! ::zoom (+ (.-zoom camera) 0.1) 10 identity)
      \↑ (move-player 0 -1)
      \↓ (move-player 0 1)
      \→ (move-player 1 0)
      \← (move-player -1 0)
      :else (prn k))))

(defn input-processor ^InputProcessor []
  (let-reg [grid-width grid-height cell-width cell-height]
    (SquidInput.
     (reify SquidInput$KeyHandler
       (handle [​this key alt ctrl shift]
         (handle-key key alt ctrl shift)))
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
        (scrolled [this amountX amountY] false))))))

(defn put-map []
  (let-reg [grid-width grid-height visible seen layers line-dungeon]
    (dotimes [x grid-width]
      (dotimes [y grid-height]
        (cond
          (< 0.0 (aget-dd visible x y))
          (.putWithLight layers
                         (int x) (int y)
                         ^char (aget-cc line-dungeon x y)
                         #_(.toFloatBits SColor/AZUL) (float -1.6980628E38)
                         (.toFloatBits SColor/DB_INK)
                         (.toFloatBits SColor/CREAM)
                         (aget-dd visible x y)
                         )
          (.contains seen x y)
          (.put layers
                x y
                ^char (aget-cc line-dungeon x y)
                (SColor/lerpFloatColors (.toFloatBits SColor/CREAM)
                                        (.toFloatBits SColor/DB_INK)
                                        0.45)
                (.toFloatBits SColor/DB_INK)
                ))))))

(defn create []
  (register ^String seed "applejack"
            ^double grid-width 280
            ^double grid-height 225
            ^double cell-width 24
            ^double cell-height 36
            ^double player-speed 4
            tweens (atom {})
            ^OrthographicCamera camera (doto (OrthographicCamera.)
                                         (.setToOrtho false
                                                      (.getWidth Gdx/graphics)
                                                      (.getHeight Gdx/graphics)))
            ^Batch batch (FilterBatch.)
            ^Viewport viewport (ScreenViewport. camera)
            ^Stage stage (Stage. viewport batch)

            ^SparseLayers layers (doto (SparseLayers. grid-width grid-height cell-width cell-height
                                                      (DefaultResources/getCrispDejaVuFont))
                                   (.setPosition 0 0))
            ^SquidInput squid-input (input-processor)
            ^IRNG rng (GWTRNG. seed)
            ^DungeonGenerator dungeon-gen (DungeonGenerator. grid-width grid-height rng)

            ^"[[C" dungeon (.generate ^DungeonGenerator dungeon-gen)
            ^"[[C" line-dungeon (DungeonUtility/hashesToLines dungeon)
            ^"[[D" resistance (DungeonUtility/generateResistances dungeon)
            ^"[[D" visible (make-array Double/TYPE grid-width grid-height)

            ^GreasedRegion blockage (GreasedRegion. visible 0.0)
            ^GreasedRegion seen (.copy (.not blockage))
            ^GreasedRegion currently-seen (.copy seen)
            _ (.fringe8way blockage)

            ^Coord player-coord (.singleRandom (GreasedRegion. ^"[[C" line-dungeon \.) rng)
            ^long player-x (.x player-coord)
            ^long player-y (.y player-coord)
            ^TextCellFactory$Glyph player-glyph (.glyph layers \@
                                                        (.toFloatBits SColor/BRIGHT_PINK)
                                                        player-x
                                                        player-y)
            tweenables {::player-x (reify Tweenable
                                     (<< [_] (.getX player-glyph))
                                     (>> [_ v] (.setX player-glyph v)))
                        ::player-y (reify Tweenable
                                     (<< [_] (.getY player-glyph))
                                     (>> [_ v] (.setY player-glyph v)))
                        ::zoom (reify Tweenable
                                 (<< [_] (.-zoom camera))
                                 (>> [_ v] (set! (.-zoom camera) v)))}
            _ (prn [(.getX player-glyph) (.getY player-glyph)])
            _
            (do
              (FOV/reuseFOV resistance visible player-x player-y 9 Radius/CIRCLE)
              (.setInputProcessor Gdx/input squid-input)
              #_(.fillBackground layers SColor/CREAM)
              (put-map)
              (.addActor stage layers))))

(defn delta ^double []
  (.getDeltaTime Gdx/graphics))

(defn render []
  (try
    (.glClearColor Gdx/gl 0 0 75 1)
    (.glClear Gdx/gl GL20/GL_COLOR_BUFFER_BIT)
    (let-reg [stage squid-input tweens camera player-glyph tweenables]
      (set! (.x (.-position camera)) (.getX player-glyph))
      (set! (.y (.-position camera)) (.getY player-glyph))
      (let [delta (delta)]
        (swap! tweens
               (fn [tweens]
                 (into
                  {}
                  (remove nil?)
                  (for [[key [^double a ^double from ^double to ^double speed
                              tween-fn]] tweens
                        :let [a (+ a (* speed delta))
                              t (get tweenables key)]]
                    (when (< a 1.0)
                      (>> t (+ from (* (- to from) (double (tween-fn a)))))
                      [key [a from to speed tween-fn]]))))))
      (put-map)
      (when (.hasNext squid-input)
        (.next squid-input))
      (.draw stage))
    (catch Exception e
      (prn e))))

(defn resize [width height]
  (.update ^Viewport (rget :viewport) width height true))

(defn new-gdx-app []
  (proxy [ApplicationAdapter] []
    (create [] (create))
    (render [] (try (render) (catch Exception e (println e))))
    (resize [width height] (resize width height))
    (dispose []
      (doseq [[k v] @registry]
        (when (instance? Disposable v)
          (println "Disposing" k)
          (.dispose ^Disposable v))))))

(defn cleanup! []
  (.exit Gdx/app)
  (reset! registry {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tap-cc> [cc]
  (tap> (apply map (comp (partial apply str) vector) cc)))

(comment
  (do
    (cleanup!)
    (start!)
    ))
