(ns com.github.plexus.macros
  (:require [clojure.string :as str]
            [clojure.reflect :as reflect]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defmacro defmutableclass
  "Like deftype or defrecord, but makes all fields mutable, and adds methods for
  accessing all fields. The main use case is bundling several types with
  type-preserving access. The getter has the same name as the field, the setter
  has a `set` prefix. Field names must be valid Java names, they are not
  munged."
  {:style/indent [2 :form :form [1]]}
  [name fields & body]
  (let [fields (for [field fields]
                 (vary-meta field update :tag
                            (fn [tag]
                              (when (and tag
                                         (symbol? tag)
                                         (not ('#{double long} tag)))
                                (symbol (.getName ^Class (resolve tag)))))))
        iname (symbol (str name "Methods"))]
    `(do
       (definterface ~iname
         ~@(for [field fields
                 impl [`(~field [])
                       `(~(symbol (str "set" (str/capitalize (str field))))
                         [~(with-meta 'v (meta field))])]]
             impl))
       (deftype ~name [~@(for [field fields]
                           (vary-meta field assoc :volatile-mutable true))]
         ~iname
         ~@(for [field fields
                 impl [`(~field [_#] ~field)
                       `(~(symbol (str "set" (str/capitalize (str field))))
                         [this# ~(with-meta 'v (meta field))]
                         (set! ~field ~'v)
                         this#)]]
             impl)
         ~@body))))

(defmacro let-fields
  "Destructure Java objects. Bindings consist of a vector of symbols to bind to,
  and a tagged symbol referring to a Java object. The fields are bound to any
  public fields or getter methods that are found on the type of the source
  symbol, based on its tag."
  [bindings & body]
  (let [[fields obj] bindings
        members (:members (reflect/reflect (resolve (:tag (meta obj)))))
        methods (for [field fields
                      :let [capitalize #(if (< (.length ^String %) 2)
                                          (.toUpperCase ^String %)
                                          (str (.toUpperCase (subs % 0 1)) (subs % 1)))
                            method (some #(when (#{field
                                                   (symbol (str "get" (capitalize (str field))))}
                                                 (:name %))
                                            %)
                                         (filter (comp :public :flags) members))
                            tag (:return-type method)]]
                  [tag
                   field
                   (symbol (str "." (:name method)))])]
    `(let [~@(for [[tag field method] methods
                   v [field (list method obj)]]
               v)]
       ~@(if-let [bindings (nnext bindings)]
           [`(let-fields ~(map (fn [field]
                                 (if-let [[t] (some #(when (= field (second %)) %) methods)]
                                   (vary-meta field assoc :tag t)
                                   field))
                               bindings) ~@body)]
           body))))
