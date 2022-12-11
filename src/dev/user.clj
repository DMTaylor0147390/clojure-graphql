(ns user
  (:require [stuff.core]
            [stuff.schema]
            [stuff.server]
            [clojure.data.json :as json]
            [nrepl.server :as nrepl]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [com.walmartlabs.lacinia :refer [execute]]
            [stuff.schema :refer [load-schema]]))

(def got-schema (load-schema))

(defn p [] (println "Hello World!"))

(def server (atom nil))

(defn start-server []
  (reset! server (nrepl/start-server :port 7777 :bind "0.0.0.0"))
  )

(defn stop-server []
  (do
    (nrepl/stop-server @server)
    (reset! server nil)))

(defn start-application [& args]
  (do
    (start-server)
    (stuff.server/start-server))
  )

(defn q
  [query-string]
  (execute got-schema query-string nil nil))
