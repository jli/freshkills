(ns freshkills.core
  (:use [compojure.core]
        [compojure.route :only [resources]]
        [ring.adapter.jetty :only [run-jetty]]
        [ring.util.response :only [response file-response]]
        [ring.middleware.params :only [wrap-params]]
        [ring.middleware.reload :only [wrap-reload]]
        [ring.middleware.stacktrace :only [wrap-stacktrace]]
        [ring.middleware.gzip :only [wrap-gzip]]
        [remvee.ring.middleware.basic-authentication :only [wrap-basic-authentication]]
        [clojure.contrib.command-line :only [with-command-line]])
  (:require [swank.swank]
            [freshkills.dump])
  (:gen-class))

(defroutes base
  (POST "/post" [txt] (freshkills.dump/post txt))
  (GET "/get" [laterthan] (freshkills.dump/get-posts laterthan))
  (GET "/rm" [id] (freshkills.dump/remove-post id))
  (POST "/edit" [id txt] (freshkills.dump/edit-post id txt))
  (GET "/love" [] (response "<3"))
  (GET "/" [] (file-response "resources/public/index.html"))
  (resources "/")
  (ANY "*" [] (file-response "resources/public/index.html")))

;; defonce to avoid rereading
(defonce allowed-users
  (try (let [res (read-string (slurp "users.dat"))]
         (println "users:" res)
         res)
       (catch Exception e
         (println "error while reading users.dat auth file. no auth!" e))))

(defn auth? [user pass]
  ;; (println "auth attempt:" user pass)
  (or (empty? allowed-users) ;; no users configured -> no auth not
      ;; equivalent to (= pass (allowed-users user)). tries user, pass
      ;; "nil, nil" first, which would be true!
      (= pass (allowed-users user :not-in-list-so-fail))))

(def app
     (-> base
         wrap-params
         wrap-gzip
         (wrap-basic-authentication auth?)
         (wrap-reload '(freshkills.core freshkills.dump))
         wrap-stacktrace))

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
