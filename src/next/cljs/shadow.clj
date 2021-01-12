(ns next.cljs.shadow
  (:refer-clojure :exclude [flush])
  (:require [cljs.compiler :as cljs]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def ^{:doc "Path relative to :next.cljs/output-dir where the js files generated
             by the cljs compiler will be written."}
  cljs-src-path
  "src/cljs")

(def ^{:doc "Path relative to :next.cljs/output-dir where the next.js page
             files will be written."}
  pages-src-path
  "src/pages")

(defn log-warning
  "Logs a warning to the console."
  [message]
  (println "WARNING: " message))

(defn ^:private configure
  "Updates the shadow.cljs configuration to generate :npm-modules in a format
   consumable by next.js."
  [state]
  (let [target (get-in state [:shadow.build/config :target])
        output-dir (get-in state [:shadow.build/config :output-dir])
        next-output-dir (get-in state [:shadow.build/config :next.cljs/output-dir])]
    (when-not (= target :next.cljs/next-js-app)
      (throw (IllegalArgumentException. (str "Expected target to be "
                                             ":next.cljs/next-js-app, "
                                             "found " target))))
    (when output-dir
      (log-warning (str "Found :output-dir in build config with "
                        " :target :next.cljs/next-js-app, this will be ignored.")))
    (when-not next-output-dir
      (throw (IllegalArgumentException. (str "No :next.cljs/output-dir found "
                                             "in build configuration."))))
    (-> state
        (assoc-in [:shadow.build/config :target] :npm-module)
        (assoc-in [:shadow.build/config :output-dir]
                  (str next-output-dir "/" cljs-src-path)))))

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
  [[page page-vars]]
  (doseq [var page-vars]
    (when-not (-> var :meta :next.cljs/export-as)
      (throw (IllegalArgumentException. (str "Found var " (:name var)
                                             " with :next.cljs/page "
                                             " but no :next.cljs/export-as.")))))
  (let [path
        (str pages-src-path "/" page ".js")

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
                         (->> (string/split page #"/")
                              (map (constantly ".."))
                              (string/join "/"))
                         "/cljs/"
                         js-ns
                         ".js';")))
             (string/join "\n"))]
    {:path path
     :content content}))

(defn create-pages
  "Creates a seq of maps, each representing one next.js page file that should
   be generated."
  [vars]
  (->> vars
       (map (fn [var] (assoc var
                             :js-ns (-> var :name namespace cljs/munge)
                             :js-var (-> var :name name cljs/munge))))
       (group-by #(get-in % [:meta :next.cljs/page]))
       (filter (fn [[page _]] page))
       (map create-page)))

(defn ^:private flush
  "Generates next.js page files to allow the js files created by the cljs
   compiler to be consumed by next.js."
  [state]
  (let [output-dir (get-in state [:shadow.build/config :next.cljs/output-dir])
        vars (all-vars state)
        pages (create-pages vars)]
    (doseq [{:keys [path content]} pages]
      (let [out-file (io/file output-dir path)]
        (io/make-parents out-file)
        (spit out-file content)))
    state))

(defn generate-next-js-app
  "Build hook to generate a next-js app in shadow-cljs."
  {:shadow.build/stages #{:configure :flush}}
  [state]
  (case (:shadow.build/stage state)
    :configure (configure state)
    :flush (flush state)
    state))
