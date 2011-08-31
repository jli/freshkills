(ns freshkills.dump
  (:use [ring.util.response :as response]
        ;;[ring.util.codec :only [url-decode]]
        ;;[match.core :only [match]]
        )
  (:import [org.apache.commons.lang StringEscapeUtils]))

(defn now [] (.getTime (java.util.Date.)))

(defn query-string->map [qs]
  (if (empty? qs)
    {}
    (let [assoc-list (map #(vec (.split % "=")) (.split qs "&"))
          keyworded (map (fn [[k v]] [(keyword k) v]) assoc-list)]
      (into {} keyworded))))


;;; "persistence"

(def default-db-file "db.dat")
(defn read-db
  ([] (read-db default-db-file))
  ([file]
     (try (read-string (slurp file))
          (catch Exception e
            (println "got exception while reading db:" e)
            ()))))

(defonce db (atom (read-db)))

(defn write-db
  ([] (write-db default-db-file))
  ([file] (spit file (pr-str @db))))

(def date-fmt (java.text.SimpleDateFormat. "yyyy-MM-dd"))
(def time-fmt (java.text.SimpleDateFormat. "HH:mm:ss"))
(defn time-str [d] (.format time-fmt d))
(defn date-str [d] (format "<b>%s</b> %s"
                           (.format date-fmt d)
                           (time-str d)))


(defn req->txt [req]
  (-> req
      :body
      slurp))

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

(defn post [req]
  (swap! db conj [(now) (req->txt req)])
  (write-db)
  (response "posted"))

(defn get-posts [req]
  (let [query-map (query-string->map (:query-string req))
        later-than (try (BigInteger. (:laterthan query-map))
                        (catch Exception _ nil))
        posts (if (nil? later-than)
                @db
                (take-while (fn [[date _]] (> date later-than)) @db))]
    ;; TODO hm, what does response do with the seq? blah.
    (response (str (into [] posts)))))
