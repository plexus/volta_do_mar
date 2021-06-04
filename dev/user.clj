(ns user)

(alter-var-root #'*print-namespace-maps* (constantly false))
;; (set! *print-namespace-maps* false)

(defmacro jit [sym]
  `(requiring-resolve '~sym))

(def portal-instance (atom nil))

(defn portal
  "Open a Portal window and register a tap handler for it. The result can be
  treated like an atom."
  []
  ;; Portal is both an IPersistentMap and an IDeref, which confuses pprint.
  (prefer-method @(jit clojure.pprint/simple-dispatch) clojure.lang.IPersistentMap clojure.lang.IDeref)
  ;; Portal doesn't recognize records as maps, make them at least datafiable
  (extend-protocol clojure.core.protocols/Datafiable
    clojure.lang.IRecord
    (datafy [r] (into {} r)))
  (let [p ((jit portal.api/open) @portal-instance)]
    (reset! portal-instance p)
    (add-tap (jit portal.api/submit))
    p))

;; Portal/tap helpers

(defn as-tree [obj]
  (with-meta obj {:portal.viewer/default :portal.viewer/tree}))

(defn as-table [obj]
  (with-meta obj {:portal.viewer/default :portal.viewer/table}))

(defn tree> [obj]
  (tap> (as-tree obj)))

(defn table> [obj]
  (tap> (as-table obj)))
