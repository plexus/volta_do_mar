(ns com.github.plexus.gdx
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.reflect :as reflect])
  (:import
   ;; gdx application/native stuff
   com.badlogic.gdx.ApplicationAdapter
   com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
   com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

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

(deftype BasicApp [^Camera camera ^Batch batch ^Stage stage])

(defn new-gdx-app [{:keys [create render resize dispose]}]
  (proxy [ApplicationAdapter] []
    (create [] (create))
    (render [] (render))
    (resize [width height] (resize width height))
    (dispose [] (dispose))))
