(ns com.github.plexus.tile-editor
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.reflect :as reflect]
            [com.github.plexus.macros :refer :all])
  (:import
   ;; gdx application/native stuff
   com.badlogic.gdx.ApplicationAdapter
   com.badlogic.gdx.ApplicationListener
   com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
   com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

   ;; gdx generic stuff
   com.badlogic.gdx.Gdx
   com.badlogic.gdx.Graphics
   com.badlogic.gdx.files.FileHandle 
   com.badlogic.gdx.graphics.Camera
   com.badlogic.gdx.graphics.Color
   com.badlogic.gdx.graphics.GL20
   com.badlogic.gdx.graphics.OrthographicCamera
   com.badlogic.gdx.graphics.Texture
   com.badlogic.gdx.graphics.Pixmap
   com.badlogic.gdx.graphics.Pixmap$Format
   com.badlogic.gdx.graphics.g2d.Batch 
   com.badlogic.gdx.graphics.g2d.SpriteBatch 
   com.badlogic.gdx.math.Rectangle
   com.badlogic.gdx.scenes.scene2d.Actor
   com.badlogic.gdx.scenes.scene2d.ui.Image
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

(declare render)

(defmutableclass TileEditor [^OrthographicCamera camera
                             ^Batch batch
                             ^Viewport viewport
                             ^Stage stage]
  ApplicationListener
  (create [_]
    (let-fields [[width height] ^Graphics Gdx/graphics]
      (set! camera (doto (OrthographicCamera.)
                     (.setToOrtho false width height))))
    (set! batch (SpriteBatch.))
    (set! viewport (ScreenViewport. camera))
    (set! stage (Stage. viewport batch)))
  (render [this]
    (render this))
  (resize [_ w h]
    (.update viewport w h true))
  (dispose [_]
    (.dispose stage)
    (.dispose batch))
  (pause [_])
  (resume [_]))

(defn render [editor]
  (let-fields [[stage] ^TileEditor editor
               [batch] stage]
    (try
      (.glClearColor Gdx/gl 0 0 0 1)
      (.glClear Gdx/gl GL20/GL_COLOR_BUFFER_BIT)
      (.draw stage)
      ;; (.begin batch)
      ;; (.draw batch ^Texture txt 0.0 0.0)
      ;; (.end batch)
      (catch Exception e
        (println "Error in rendering" e)
        (run! (println (.getStackTrace e)))))))

(def editor (TileEditor. nil nil nil nil))

(defn lwjgl3-config []
  (doto (Lwjgl3ApplicationConfiguration.)
    (.setTitle "volta do mar")
    (.setWindowedMode 800 480)))

(defn start! []
  (future
    (Lwjgl3Application. editor (lwjgl3-config))))

(start!)

(.postRunnable Gdx/app (fn []
                         #_(def pxm (Pixmap. 24 36 Pixmap$Format/RGBA8888))
                         (.setColor pxm Color/RED)
                         #_(.fill pxm)
                         (.drawPixel pxm 5 5 (Color/rgba8888 Color/RED))
                         (.draw txt pxm 0 0)
                         #_(def txt (Texture. pxm))
                         #_(def img (Image. txt))))

;; 

;; 
;; (.setSize img 100 200)

(let-fields [[stage] ^TileEditor editor]
  (.addActor stage img))
;; img

;; (bean (.getTextureData txt))

