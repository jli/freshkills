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

;;; madness #"(?i)(https?://[\\$-_@\\.&\\+!\\*\"'\\(\\),%:;#a-zA-Z0-9/]+)"
(def link-rex #"(?i)([a-z]+://\S+)")

(defn linkify [s]
  (-> (.matcher link-rex s)
      (.replaceAll "<a href=\"$1\">$1</a>")))


(defonce db (atom ()))

(def date-fmt (java.text.SimpleDateFormat. "yyyy-MM-dd"))
(def time-fmt (java.text.SimpleDateFormat. "HH:mm:ss"))
(defn time-str [d] (.format time-fmt d))
(defn date-str [d] (format "<b>%s</b> %s"
                           (.format date-fmt d)
                           (time-str d)))


(defn req->txt [req]
  (-> req
      :body
      slurp
      (drop-prefix "txt=")
      url-decode))


;; make url a link
(defn format-dump [s]
  (linkify (StringEscapeUtils/escapeHtml s)))

(defn date-day [date]
  (let [cal (doto (java.util.Calendar/getInstance)
              (.setTime date))
        field (fn [field] (.get cal field))]
    (.get cal java.util.Calendar/DAY_OF_YEAR)))

;;; only shows day once in a sequence
(defn db-format-date [db]
  (let [prev-day (atom false)]
    (map (fn [[date val]]
           (let [day (date-day date)]
             (if (= day @prev-day)
               [(time-str date) val]
               (do (swap! prev-day (constantly day))
                   [(date-str date) val]))))
         db)))

(defn db-html [db]
  (map (fn [[date val]]
         (format "<div>%s - %s</div>"
                 date (format-dump val)))
       db))

(defn main-page [req post?]
  (when post?
    (swap! db conj [(now) (req->txt req)]))
  (let [db-html (-> @db
                    db-format-date
                    db-html)]
    (format "<html><head><title>dump</title></head><body>
<img style=\"float: right;\" height=\"350\" src=\"horseshoe.png\"><h1>%s</h1>
<form action=\"/post\" method=\"post\">
<textarea name=\"txt\" style=\"width: 60%%;\"></textarea><br>
<input type=\"submit\" id=\"submit\" value=\"YEAH\"></input>
</form>
<h2>dumped</h2>
<div>%s</div>
</body></html>" (embiggen "freshkills") (apply str db-html))))

(defn handler [req]
  ;; guh. :query-string always in req, but can be nil.
  (let [post? (.startsWith (:uri req) "/post")]
    (response (str (main-page req post?)))))
