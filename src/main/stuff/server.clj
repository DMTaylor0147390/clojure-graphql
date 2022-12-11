(ns stuff.server
  (:require [ring.adapter.jetty :as jetty]
            [ring.util.request :as request]
            [reitit.ring :as ring]
            [com.walmartlabs.lacinia :as lacinia]
            [clojure.data.json :as json]
            [nrepl.server :as nrepl]
            [ring.middleware.resource :as resource]
            [ring.middleware.content-type :as content-type]
            [ring.middleware.not-modified :as not-modified]
            [stuff.schema :as schema]))

(defn got-schema [] (schema/load-schema))
;;
(defn graphql-handler [request]
  (let [grapql-request (json/read-str (request/body-string request) :key-fn keyword)
        {:keys [query variables]} grapql-request
        result (lacinia/execute (got-schema) query variables nil)]
    {:status  200
     :body    (json/write-str result)
     :headers {"Content-Type" "application/json"}}))

(defn deez-handler [request]
  {:status 200
   :body (json/write-str "nuts")
   :headers {"Content-Type" "application/json"}})

(defn base-handler [request]
  {:status 200
   :body (json/write-str "foobar")
   :headers {"Content-Type" "application/json"}})

;;
(def app
  (-> (ring/ring-handler
       (ring/router [["/" {:get base-handler}]
                     ["/deez" {:post deez-handler}]
                     ["/graphql" {:post graphql-handler}]]))
      (resource/wrap-resource "static")
      content-type/wrap-content-type
      not-modified/wrap-not-modified))

;;
(defonce server (atom nil))

(defn start-server [& args]
  (println args)
  (reset! server (jetty/run-jetty app {:join? false
                                       :port  8080})))

(defn stop-server []
  (when @server
    (.stop @server)))

(defn restart-server []
  (do
    (stop-server)
    (start-server)))

(comment
  (restart-server)
  (start-server)
  (stop-server))
