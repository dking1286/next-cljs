(ns next.cljs.shadow
  (:refer-clojure :exclude [flush]))

(defn log-warning
  [message]
  (println "WARNING: " message))

(defn ^:private configure
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
                  (str next-output-dir "/src/cljs")))))

(defn ^:private flush
  [state])

(defn generate-next-js-app
  "Build hook to generate a next-js app in shadow-cljs."
  {:shadow.build/stages #{:configure :flush}}
  [state]
  (case (:shadow.build/stage state)
    :configure (configure state)
    :flush (flush state)
    state))