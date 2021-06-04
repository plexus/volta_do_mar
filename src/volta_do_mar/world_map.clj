(ns volta-do-mar.world-map
  (:refer-clojure :exclude [for])
  (:require [criterium.core :as criterium]
            [clojure.core.protocols :as core-protocols]
            [lambdaisland.data-printers :as data-printers]
            [volta-do-mar.macros :refer [for]]))

(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)

(defprotocol IWorldMap
  (tile [world x y])
  (set-tile! [world x y tile])
  (-swap-tile! [world x y f args]))

(defn swap-tile! [world x y f & args]
  (-swap-tile! world x y f args))

(deftype WorldMap [^"[Ljava.lang.Object;" world ^long width ^long height]
  IWorldMap
  (tile [this x y]
    (aget world (+ ^long x (* ^long y width))))
  (set-tile! [this x y tile]
    (aset world (+ ^long x (* ^long y width)) tile))
  (-swap-tile! [this x y f args]
    (set-tile! this x y (apply f (tile this x y) args)))

  clojure.lang.Seqable
  (seq [this]
    (seq world))

  core-protocols/Datafiable
  (datafy [this]
    {:width width :height height}))

(data-printers/register-print WorldMap `WorldMap core-protocols/datafy)
(data-printers/register-pprint WorldMap `WorldMap core-protocols/datafy)

(defn world-map [^long width ^long height]
  (WorldMap.
   (make-array Object (* width height))
   width height))

(comment
  (defn rand-tile []
    (let [t (rand-nth [:ocean :land])]
      {:type t
       :height (if (= :ocean t)
                 (- ^long (rand-int 6000))
                 (rand-int 6000))
       :entities #{}}))

  (def world (world-map 10 10))

  (doseq [x (range 10)
          y (range 10)]
    (set-tile! world x y (rand-tile)))

  (swap-tile! world 0 0 update :entities conj :player)

  (type (seq world)))



(defprotocol ISpatialLookup
  (find-xy [coll x y]))

(deftype SpatialLeafSet [children]
  ISpatialLookup
  (find-xy [this x y]
    (transduce (filter #(and (= x (:x %)) (= y (:y %)))) conj #{} children))
  clojure.lang.IPersistentCollection
  (count [this]
    (count children))
  (cons [this obj]
    (SpatialLeafSet. (conj children obj)))
  (empty [this]
    (SpatialLeafSet. (empty children)))
  (equiv [this that]
    (and (instance? SpatialLeafSet that)
         (= children (.-children ^SpatialLeafSet that))))

  clojure.lang.IPersistentSet
  (disjoin [this item]
    (SpatialLeafSet. (disj children item)))
  (contains [this item]
    (contains? children item))
  (get [this item]
    (get children item))

  clojure.lang.Seqable
  (seq [this]
    (seq children)))

(declare spatial-set)

(defn- child-idx [^long dimension ^long capacity ^long x ^long y]
  (int (+ ^long (quot ^long (mod x capacity) dimension)
          (* ^long (quot ^long (mod y capacity) dimension) dimension))))

(deftype SpatialSet [^long level ^long dimension ^long capacity children]
  ISpatialLookup
  (find-xy [this x y]
    (let [^double x x ^double y y]
      (let [child (get children (child-idx dimension capacity x y))]
        (if child
          (find-xy child x y)
          #{}))))

  clojure.lang.IPersistentCollection
  (count [this]
    (transduce (map count) + children))
  (cons [this item]
    (let [^double x (:x item) ^double y (:y item)]
      (let [chidx (child-idx dimension capacity x y)
            child (or (get children chidx)
                      (spatial-set dimension (dec level)))]
        (SpatialSet. level
                     capacity
                     dimension
                     (assoc children chidx (conj child item))))))
  (empty [this]
    (SpatialSet. level capacity dimension (vec (repeat capacity nil))))
  (equiv [this that]
    (or (and (instance? SpatialSet that)
             (= level level)
             (= children children))
        (and (set? that)
             (= (count this) (count that))
             (reduce (fn [t i]
                       (if (.contains this i)
                         t
                         (reduced false)))
                     true
                     that))))

  clojure.lang.IPersistentSet
  (disjoin [this item]
    (SpatialSet. level
                 capacity
                 dimension
                 (update children
                         (child-idx dimension capacity (:x item) (:y item))
                         (fn [c]
                           (when c (disj c item))))))
  (contains [this item]
    (let [child (get children (child-idx dimension capacity (:x item) (:y item)))]
      (when child (contains? child item))))
  (get [this item]
    (when (.contains this item)
      item))

  clojure.lang.Seqable
  (seq [this]
    (seq (apply concat children))))

(data-printers/register-print SpatialLeafSet `SpatialLeafSet #(.-children ^SpatialLeafSet %))
(data-printers/register-pprint SpatialLeafSet `SpatialLeafSet #(.-children ^SpatialLeafSet %))

(defn spatial-set
  "Can hold :x or :y up to (Math/pow (* size size) level). Size has to be square."
  [^long size ^long level]
  (if (= 0 level)
    (SpatialLeafSet. #{})
    (let [capacity (* size size)]
      (SpatialSet. level
                   size
                   capacity
                   (into [] (repeat capacity nil))))))


(comment
  (second
   (reduce
    (fn [[coll r] item]
      (try
        [(conj coll item) r]
        (catch Exception e
          (println "Failed" item)
          [coll (conj r item)])))
    [(spatial-set 16 2) []] normal-set))

  (Math/pow (* 16 16) 2);; => 65536.0

  (def normal-set (into #{} (repeatedly 10000 #(do {:x (rand-int 65535)
                                                    :y (rand-int 65535)}))))

  (def sset (into (spatial-set 4 2) normal-set))
  (count sset)

  (defn nfind-xy [x y]
    (into #{}
          (filter #(and (= (:x %) x) (= (:y %) y)) normal-set)))

  (time
   (doseq [x (range 3000 3100)
           y (range 2000 2100)]
     (nfind-xy  x y)))
  (time
   (count (filter #(and (< 3000 (:x %) 4000)
                        (< 2000 (:y %) 3000))
                  normal-set)))
  )
(defn my-select []
  (let [selected (reagent/atom "apples")]
    (fn []
      [:select {:value @selected :on-change #(reset! selected (.. % -target -value))}
       [:option {:value "apples"} "Apples"]
       [:option {:value "pears"} "Pears"]
       [:option {:value "oranges"} "Oranges"]])))

(defn my-step [ctx]
  (if (:error ctx)
    ctx
    (try
      ,,, do work ,,,
     (assoc ctx :resource-for-the-next-step my-resource)
     (catch Exception e
       (log/error :my-step/failed {:parameter p} :exception e)
       (assoc ctx :error e :error-step "my-step")))))

(defn report-errors [{:keys [error error-step project-id timestamp] :as ctx}]
  (when error
    (send-email-to-christian
     (str "Project " project-id " failed at " timestamp " during " error-step ".\n"
          "Exception: " error "\n"
          "Stack:" (.getStackTrace error))))
  ctx)

(-> {:project-id 123
     :timestamp (Date.)
     :input (io/file "...")}
    my-step
    my-other-step
    report-errors)



(defn input-field [page id field-id validator]
  (let [initial-value (reagent/atom @(rf/subscribe [::word-field page id field-id]))
        get-value (fn [e] (-> e .-target .-value))
        reset! #(rf/dispatch [::set-word-field page id field-id @initial-value])
        save! #(do
                 (rf/dispatch [::set-word-field page id field-id %])
                 (reset! initial-value %))]
    (fn []
      (let [value @(rf/subscribe [::word-field page id field-id])
            valid? (validator value)
            changed? (not= @initial-value value)
            klass (list (cond (not valid?) "is-danger"
                              changed? "is-warning")
                        ;; braille fields should be in mono space
                        (when (#{:contracted :uncontracted} field-id) "braille"))]
        [:div.field
         [:input.input {:type "text"
                        :aria-label (tr [field-id])
                        :class klass
                        :value value
                        :on-change #(save! (get-value %))
                        :on-key-down #(when (= (.-which %) 27) (reset!))}]
         (when-not valid?
           [:p.help.is-danger (tr [:input-not-valid])])]))))
