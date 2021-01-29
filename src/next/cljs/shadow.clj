(ns next.cljs.shadow
  (:refer-clojure :exclude [flush])
  (:require [cljs.compiler :as cljs]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [next.cljs.file :as file]
            [next.cljs.utils :as utils])
  (:import [java.nio.file Paths]))

(defn ^:private path-of
  [str-path]
  (Paths/get str-path (into-array [""])))

(defn relative-import-path
  [from to]
  (let [from-path (path-of from)
        to-path (path-of to)]
    (-> (.getParent from-path)
        (.relativize to-path)
        (.toString))))

(defn log-warning
  "Logs a warning to the console."
  [message]
  (println "WARNING: " message))

(defn ^:private configure
  "Updates the shadow.cljs configuration to generate :npm-modules in a format
   consumable by next.js."
  [state]
  (let [target (get-in state [:shadow.build/config :target])]
    (when-not (= :npm-module target)
      (throw (IllegalArgumentException. ":target must be :npm-module")))
    ;; TODO: Put more validation here
    state))

(defn ^:private all-vars
  "Gets a seq of maps, each representing one of the vars from the cljs compiler
   state."
  [state]
  (for [[_ ns-info] (get-in state [:compiler-env :cljs.analyzer/namespaces])
        var-info (-> ns-info :defs vals)]
    var-info))

(defn ^:private create-page
  "Creates a map representing a page file that should be generated for the
   next.js app.
   
   Takes a two-element vector of the form [page page-vars], where 'page' is
   a string representing the relative page URL of the next.js page, and
   'page-vars' is a seq of maps representing the vars that were annotated with
   that page url in their :next.cljs/page metadata."
  [output-dir pages-dir [page page-vars]]
  (doseq [var page-vars]
    (when-not (-> var :meta :next.cljs/export-as)
      (throw (IllegalArgumentException. (str "Found var " (:name var)
                                             " with :next.cljs/page "
                                             " but no :next.cljs/export-as.")))))
  (let [path
        (str pages-dir "/" page ".js")

        content
        (->> (group-by :js-ns page-vars)
             (map (fn [[js-ns page-vars]]
                    (str "export {"
                         (->> page-vars
                              (map (fn [pv]
                                     (str (:js-var pv)
                                          " as "
                                          (-> pv :meta :next.cljs/export-as))))
                              (string/join ", "))
                         "} from '"
                         (relative-import-path
                          path
                          (str output-dir "/" js-ns ".js"))
                         "';")))
             (string/join "\n"))]
    {:path path
     :content content}))

(defn create-pages
  "Creates a seq of maps, each representing one next.js page file that should
   be generated."
  [output-dir pages-dir vars]
  (->> vars
       (map (fn [var] (assoc var
                             :js-ns (-> var :name namespace cljs/munge)
                             :js-var (-> var :name name cljs/munge))))
       (group-by #(get-in % [:meta :next.cljs/page]))
       (filter (fn [[page _]] page))
       (map #(create-page output-dir pages-dir %))))

(defn patch-cljs-env
  [])

(defn write-file
  [path content]
  (let [out-file (io/file path)]
    (io/make-parents out-file)
    (spit out-file content)))

(defn flush-files
  [files]
  (doseq [{:keys [path content]} (utils/conform! (s/coll-of ::file/file) files)]
    (write-file path content)))

(defn ^:private flush
  "Generates next.js page files to allow the js files created by the cljs
   compiler to be consumed by next.js."
  [state]
  (let [output-dir (get-in state [:shadow.build/config :output-dir])
        pages-dir (get-in state [:shadow.build/config :next.cljs/pages-dir])
        vars (all-vars state)
        pages (create-pages output-dir pages-dir vars)]
    (flush-files pages)
    state))

(defn generate-next-js-app
  "Build hook to generate a next-js app in shadow-cljs."
  {:shadow.build/stages #{:configure :flush}}
  [state]
  (case (:shadow.build/stage state)
    :configure (configure state)
    :flush (flush state)
    state))
