(ns next.cljs.examples.shadow-cljs
  (:require ["react" :as react]))

(defn main-page
  {:export true
   :next.cljs/page "index"
   :next.cljs/export-as "default"}
  [props]
  (react/createElement "div" #js {} "Hello world"))
