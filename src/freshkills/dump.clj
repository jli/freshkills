(ns freshkills.dump
  (:use [ring.util.response :as response]
        [ring.util.codec :only [url-decode]]
        ;;[match.core :only [match]]
        )
  (:import [org.apache.commons.lang StringEscapeUtils]))

(defn now [] (java.util.Date.))

(defn drop-prefix [s pre]
  (if (.startsWith s pre)
    (.substring s (count pre))
    s))

;;; madness #"(?i)(https?://[\\$-_@\\.&\\+!\\*\"'\\(\\),%:;#a-zA-Z0-9/]+)"
(def link-rex #"(?i)([a-z]+://\S+)")

(defn linkify [s]
  (-> (.matcher link-rex s)
      (.replaceAll "<a href=\"$1\">$1</a>")))


(def default-db-file "db.dat")
(defn read-db
  ([] (read-db default-db-file))
  ([file]
     (try (let [raw (read-string (slurp file))]
            (map (fn [[date-ms str]]
                   [(java.util.Date. date-ms) str])
                 raw))
          (catch Exception e
            (println "got exception while reading db:" e)
            ()))))

(defonce db (atom (read-db)))

(defn write-db
  ([] (write-db default-db-file))
  ([file]
     (let [serial-db (map (fn [[jdate str]] [(.getTime jdate) str])
                          @db)]
       (spit file (pr-str serial-db)))))

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
      ;;(drop-prefix "txt=")
      ;;url-decode
      ))

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
         (format "<div>%s - <pre>%s</pre></div>"
                 date (format-dump val)))
       db))


(defn post [req]
  (swap! db conj [(now) (req->txt req)])
  (write-db)
  (response "posted"))

(defn get-posts [_req]
  (-> @db
      db-format-date
      db-html
      response))
