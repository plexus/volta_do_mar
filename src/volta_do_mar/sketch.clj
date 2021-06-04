(ns volta-do-mar.sketch
  (:refer-clojure :exclude [for])
  (:require [criterium.core :as criterium]
            [lambdaisland.data-printers :as data-printers]
            [volta-do-mar.macros :refer [for]])
  )

(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)

;; (comment

;;   Boat
;;   -> kind of weird dual between entity and map

;;   :floating
;;   [:material :wood]
;;   )


;; {:fixtures [:left-arm
;;             :right-arm
;;             :head]}

;; {:slots {:left-arm {:capacity }}}


;; :container
;; - is a set
;; - capacity

;; :mapped
;; - bounding box
;; - item at x/y

;; (Math/sqrt (* 2000 9 2000))
;; ;; => 36 000 000

;; (* 25 3 25)
;; ;; => 1875

;; (* 80 3 80)
;; ;; => 19200

;; 75 / 240

;; ;; Map size
;; ;; 2k ~ 20k per dimension

;; Base map
;; - ocean
;; - land
;; - depth/height?

(defn rand-tile []
  (let [t (rand-nth [:ocean :land])]
    {:type t
     :height (if (= :ocean t)
               (- ^long (rand-int 6000))
               (rand-int 6000))
     :entities #{}}))

(defn nested-world [size type]
  (for [_ (range size)
        :into type]
    (for [_ (range size)
          :into type]
      (rand-tile))))

(defn flat-world [size type]
  (for [_ (range size)
        _ (range size)
        :into type]
    (rand-tile)))

(defn world-avg [world size lookup]
  (/ ^long (reduce #(+ ^long %1 ^long %2)
                   (for [x (range size)
                         y (range size)]
                     (lookup world x y)))
     (* ^long size ^long size)))

(def size 1000)

(do
  (println "===== nested vectors")
  (let [world (nested-world size [])]
    (criterium/quick-bench
     (world-avg world size #(get-in %1 [%2 %3 :height]))
     ))

  (println "===== flat vectors")
  (let [world (flat-world size [])]
    (criterium/quick-bench
     (world-avg world size #(get-in %1 [(+ (* ^long %2 ^long size) ^long %3) :height]))
     ))

  (println "===== nested arrays")
  (let [world (nested-world size :array)]
    (criterium/quick-bench
     (world-avg world size #(:height (aget %1 %2 %3)))
     ))

  (println "===== flat arrays")
  (let [world (flat-world size :array)]
    (criterium/quick-bench
     (world-avg world size #(:height (aget ^"[Ljava.lang.Object;" %1 (+ (* ^long %2 ^long size) ^long %3))))
     )))
