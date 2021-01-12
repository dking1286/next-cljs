(ns next.cljs.file 
  (:require [clojure.spec.alpha :as s]))

(s/def ::path string?)
(s/def ::content string?)
(s/def ::file (s/keys :req-un [::path ::content]))
