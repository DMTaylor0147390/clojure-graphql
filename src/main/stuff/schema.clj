(ns stuff.schema
  (:require [clojure.data.json :as json]
            [clojure.java.jdbc :as sql]
            [monger.core :as mg]
            [monger.collection :as mc]
            [taoensso.carmine.message-queue :as car-mq]
            [taoensso.carmine :as car :refer (wcar)]
            [clojure.edn :as edn]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia :refer [execute]]
            [clojure.pprint :refer [pprint]]))

(defn index-by [coll key-fn]
  (into {} (map (juxt key-fn identity) coll)))

(def books {})

(def houses {})

(def characters {})

(defn get-book [context {:keys [id]} value]
  (get books id))

(defn get-character [context {:keys [id]} value]
  (get characters id))

(defn get-house [id]
  (get houses id))

(defn get-allegiances [context args {:keys [Allegiances]}]
  (map get-house Allegiances))


(defn get-books [context args {:keys [Books]}]
  (map #(get books %) Books))

(def server1-conn {:pool {} :spec {:uri "redis://cache:6379"}})
(defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

(defn add-kv-to-redis [k v]
  (wcar* (car/set k v)))

(def mongo-conn {:host "db" :port 27017})

(defn add-doc-to-mongo [doc]
  (let [conn (mg/connect mongo-conn)
        db   (mg/get-db conn "monger-test")]
    (mc/insert-and-return db "documents" doc)))

(def pg-db {:dbtype "postgresql"
            :port 5432
            :user "postgres"
            :dbname "pg-1"
            :password "mysecretpassword"})



(comment
  (sql/get-connection pg-db)
  (wcar* (car/ping)) ; => "PONG" (1 command -> 1 reply)

  (wcar*
   (car/ping)
   (car/set "foo" "baz")
   (car/get "foo")) ; => ["PONG" "OK" "bar"] (3 commands -> 3 replies)

  (wcar* (car/set "clj-key" {:bigint (bigint 31415926535897932384626433832795)
                             :vec    (vec (range 5))
                             :set    #{true false :a :b :c :d}
                             :bytes  (byte-array 5)
                             ;; ...
                             })
         (car/get "clj-key"))

  (let [conn (mg/connect {:host "db" :port 27017})
        db   (mg/get-db conn "monger-test")]
    (mc/find-maps db "documents"))

  (let [conn (mg/connect {:host "db" :port 27017})
        db   (mg/get-db conn "monger-test")]
    (mc/insert-and-return db "documents" {:name "Johnny" :age 40}))

  (add-doc-to-mongo {:name "Johnny" :age 40})
  )


;(def server1-conn {:pool {} :spec {:uri "redis://127.0.0.1:6379"}})
;(def server1-conn {:pool {} :spec {:uri "redis://default:redispw@localhost:55000"}})
;(def server1-conn {:pool {} :spec {:uri "redis://localhost:6379"}})
;; (defmacro wcar* [& body] `(car/wcar server1-conn ~@body))

;; (def my-worker
;;   (car-mq/worker server1-conn "my-queue"
;;                  {:handler (fn [{:keys [message attempt]}]
;;                              (println "Received" message)
;;                              {:status :success})}))

(defn resolver-map []
  {:query/book-by-id get-book
   :query/character-by-id get-character
   :Character/Allegiances get-allegiances
   :Character/Books get-books
   :world (constantly "Twitter!")
   :add-to-mongo (fn [& [ctx args value]]
                   (clojure.pprint/pprint args)
                   (add-doc-to-mongo (:m args)))
   :add-to-redis (fn [& [ctx args value]]
                   (add-kv-to-redis (:k args) (:v args)))})

(defn inspect [x]
  (do
    (println x)
    x))

;; == schema ==
(defn load-schema
  []
  (-> "resources/schema.edn"
      slurp
      edn/read-string
      (merge {:scalars {:map {:parse #(if (string? %) (clojure.edn/read-string %) %)
                              :serialize #(if (string? %) (clojure.edn/read-string %) %)}}})
      (attach-resolvers (resolver-map))
      schema/compile))

(comment
  (load-schema))
