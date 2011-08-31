(ns freshkills.dump
  (:use [ring.util.response :as response]
        ;;[ring.util.codec :only [url-decode]]
        ;;[match.core :only [match]]
        )
  ;;(:import [org.apache.commons.lang StringEscapeUtils])
  )

;;; utils

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



;;; requests

(defn req->txt [req]
  (-> req
      :body
      slurp))

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
