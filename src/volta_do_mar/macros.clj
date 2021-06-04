(ns volta-do-mar.macros)

(defmacro for
  {:style/indent 1}
  [binding & body]
  (let [pairs (partition 2 binding)
        coll (some #(when (= :into (first %))
                      (second %))
                   pairs)
        binding (into [] cat (remove #(= :into (first %)) pairs))]
    (let [form `(clojure.core/for ~binding
                  ~@body)
          form (if coll
                 `(if (= :array ~coll)
                    (into-array ~form)
                    (into ~coll ~form))
                 form)]
      form)))
