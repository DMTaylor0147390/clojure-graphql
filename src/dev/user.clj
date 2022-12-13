(ns user
  (:require [stuff.core]
            [stuff.schema]
            [stuff.server]
            [clojure.data.json :as json]
            [clojure.tools.logging :refer [info]]
            [nrepl.server :as nrepl]
            [clojure.pprint :refer [pprint]]
            [clojure.edn :as edn]
            [com.walmartlabs.lacinia :refer [execute]]
            [jackdaw.admin :as ja]
            [jackdaw.serdes.edn :as jse]
            [jackdaw.streams :as j]
            [jackdaw.client :as jc]
            [jackdaw.client.log :as jcl]
            [stuff.schema :refer [load-schema]])
  (:import
   (org.apache.kafka.common.serialization Serdes)))

(def got-schema (load-schema))

(defn p [] (println "Hello World!"))

(defn topic-config
  "Takes a topic name and returns a topic configuration map, which may
  be used to create a topic or produce/consume records."
  [topic-name]
  {:topic-name topic-name
   :partition-count 1
   :replication-factor 1
   :key-serde (jse/serde)
   :value-serde (jse/serde)})

(defn app-config
  []
  {"application.id" "pipe"
   "bootstrap.servers" "localhost:9092"
   "cache.max.bytes.buffering" "0"})

(defn kafka-admin-client-config
  []
  {"bootstrap.servers" "localhost:9092"})

(def kafka-system nil)

(defn create-topic
  "Takes a topic config and creates a Kafka topic."
  [topic-config]
  (with-open [client (ja/->AdminClient (kafka-admin-client-config))]
    (ja/create-topics! client [topic-config])))

(defn list-topics
  "Returns a list of Kafka topics."
  []
  (with-open [client (ja/->AdminClient (kafka-admin-client-config))]
    (ja/list-topics client)))

(defn topic-exists?
  "Takes a topic name and returns true if the topic exists."
  [topic-config]
  (with-open [client (ja/->AdminClient (kafka-admin-client-config))]
    (ja/topic-exists? client topic-config)))

(def producer-config
  {"bootstrap.servers" "localhost:9092"})

(def consumer-config
  {"bootstrap.servers" "localhost:9092"
   "auto.offset.reset" "earliest"
   "enable.auto.commit" "false"
   "key.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"
   "value.deserializer" "org.apache.kafka.common.serialization.StringDeserializer"
   "group.id" "stuff.my-consumer"})

(defn publish
  "Takes a topic config and record value, and (optionally) a key and
  parition number, and produces to a Kafka topic."
  ([topic-config value]
   (with-open [client (jc/producer producer-config topic-config)]
     @(jc/produce! client topic-config value))
   nil)

  ([topic-config key value]
   (with-open [client (jc/producer producer-config topic-config)]
     @(jc/produce! client topic-config key value))
   nil)

  ([topic-config partition key value]
   (with-open [client (jc/producer producer-config topic-config)]
     @(jc/produce! client topic-config partition key value))
   nil))

(defn get-records
  "Takes a topic config, consumes from a Kafka topic, and returns a
  seq of maps."
  ([topic-config]
   (get-records topic-config 2000))

  ([topic-config polling-interval-ms]
   (let [client-config (assoc consumer-config
                              "group.id"
                              (str (java.util.UUID/randomUUID)))]
     (with-open [client (jc/subscribed-consumer client-config [topic-config])]
       (doall (jcl/log client 100 seq))))))

(defn get-keyvals
  "Takes a topic config, consumes from a Kafka topic, and returns a
  seq of key-value pairs."
  ([topic-config]
   (get-keyvals topic-config 20))

  ([topic-config polling-interval-ms]
   (map (juxt :key :value) (get-records topic-config polling-interval-ms))))

(defn build-topology
  "Reads from a Kafka topic called `input`, logs the key and value,
  and writes these to a Kafka topic called `output`. Returns a
  topology builder."
  [builder]
  (-> (j/kstream builder (topic-config "input"))
      (j/peek (fn [[k v]]
                (info (str {:key k :value v}))))
      (j/to (topic-config "output")))
  builder)

(defn create-topics
  "Takes a list of topics and creates these using the names given."
  [topic-config-list]
  (with-open [client (ja/->AdminClient (kafka-admin-client-config))]
    (ja/create-topics! client topic-config-list)))

(defn start-app
  "Starts the stream processing application."
  [app-config]
  (let [builder (j/streams-builder)
        topology (build-topology builder)
        app (j/kafka-streams topology app-config)]
    (j/start app)
    (info "pipe is up")
    app))

(defn stop-app
  "Stops the stream processing application."
  [app]
  (j/close app)
  (info "pipe is down"))

(defn re-delete-topics
  "Takes an instance of java.util.regex.Pattern and deletes any Kafka
  topics that match."
  [re]
  (with-open [client (ja/->AdminClient (kafka-admin-client-config))]
    (let [topics-to-delete (->> (ja/list-topics client)
                                (filter #(re-find re (:topic-name %))))]
      (ja/delete-topics! client topics-to-delete))))

(defn stop
  "Stops the app and deletes topics.
  This functions is required by the `user` namespace and should not
  be called directly."
  []
  (when kafka-system
    (stop-app (:app kafka-system)))
  (re-delete-topics #"(input|output)"))

(defn start
  "Creates topics, and starts the app.
  This functions is required by the `user` namespace and should not
  be called directly."
  []
  (with-out-str (stop))
  (create-topics (map topic-config ["input" "output"]))
  {:app (start-app (app-config))})

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
    (stuff.server/start-server)
    (start)))

(defn q
  [query-string]
  (execute got-schema query-string nil nil))
