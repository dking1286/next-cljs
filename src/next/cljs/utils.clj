(ns next.cljs.utils 
  (:require [clojure.spec.alpha :as s]))

(defn conform!
  "Conforms a value to a spec, raising an error if the value does not match."
  [spec x]
  (let [result (s/conform spec x)]
    (when (= result ::s/invalid)
      (throw (ex-info "Value did not conform to spec."
                      {:type :spec-mismatch
                       :explain-str (s/explain-str spec x)
                       :explain-data (s/explain-data spec x)})))
    result))
