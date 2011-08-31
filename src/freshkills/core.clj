(ns freshkills.core
  (:use [ring.adapter.jetty :only [run-jetty]]
        [ring.util.response :as response]
        [ring.middleware.file :only [wrap-file]]
        [ring.middleware.reload :only [wrap-reload]]
        [ring.middleware.stacktrace :only [wrap-stacktrace]]
        [clojure.contrib.command-line :only [with-command-line]])
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

(defn -main [& args]
  ;; there should be some sweet thing that handles parseInt automatically.
  (with-command-line args
    "FRESHKILLS INC....................."
    [[jetty-port j "jetty port" "8080"]
     [no-swank? "don't start swank server" false]
     [swank-port s "swank port" "8081"]]
    (when (not no-swank?)
      (swank.swank/start-server :port (Integer/parseInt swank-port)))
    (run-jetty #'app {:port (Integer/parseInt jetty-port)})))
