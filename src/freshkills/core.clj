(ns freshkills.core
  (:use [ring.adapter.jetty :only [run-jetty]]
        [ring.util.response :as response]
        [ring.middleware.file :only [wrap-file]]
        [ring.middleware.reload :only [wrap-reload]]
        [ring.middleware.stacktrace :only [wrap-stacktrace]])
  (:require [swank.swank]
            [freshkills.dump])
  (:gen-class))

(defn base [req]
  (condp #(.startsWith %2 %1) (:uri req)
    "/love" (response/response "<3")
    "/post" (freshkills.dump/post req)
    "/get" (freshkills.dump/get-posts req)
    (response/file-response "index.html")))

(def app
  (-> base
      (wrap-reload '(freshkills.core freshkills.dump))
      (wrap-file ".")
      (wrap-stacktrace)))

(defn -main []
  (swank.swank/start-server :port 8081)
  (run-jetty #'app {:port 8080}))
