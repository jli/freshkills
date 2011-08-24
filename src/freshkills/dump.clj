(ns freshkills.dump
  (:use [ring.util.response :as response]
        [ring.util.codec :only [url-decode]]
        ;;[match.core :only [match]]
        )
  (:import [org.apache.commons.lang StringEscapeUtils]))

(defn now [] (java.util.Date.))

(def embiggen
  (comp (partial apply str) (partial interpose " ") #(.toUpperCase %)))

(defn drop-prefix [s pre]
  (if (.startsWith s pre)
    (.substring s (count pre))
    s))

(def page-title (embiggen "FRESHKILLS"))

(defonce db (atom {}))

(def date-fmt (java.text.SimpleDateFormat.))
(defn date-str [d] (.format date-fmt d))

(defn req->txt [req]
  (-> req
      :body
      slurp
      (drop-prefix "txt=")
      url-decode
      ))

;; make url a link
(defn format-dump [s]
  (StringEscapeUtils/escapeHtml s))

(defn main-page [req post?]
  (when post?
    (swap! db assoc (now) (req->txt req)))
  (let [db-fmt (map (fn [[k v]]
                      (format "<div><b>%s</b> - %s</div>"
                              (date-str k)
                              (format-dump v)))
                    @db)]
    (format "<html><head><title>dump</title></head><body>
<img style=\"float: right;\" height=\"350\" src=\"horseshoe.png\"><h1>%s</h1>
<form action=\"/post\" method=\"post\">
<input type=\"textarea\" name=\"txt\" style=\"width: 50%%; height: 10%%;\"></input><br>
<input type=\"submit\" id=\"submit\" value=\"YEAH\"></input>
</form>
<h1>PUSHES</h1>
<div>%s</div>
</body></html>" page-title (apply str db-fmt))))

(defn handler [req]
  ;; guh. :query-string always in req, but can be nil.
  (let [post? (.startsWith (:uri req) "/post")]
    (response (str (main-page req post?)))))
