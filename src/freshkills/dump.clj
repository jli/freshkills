(ns freshkills.dump
  (:use [ring.util.response :only [response]]))



;;; utils

(defn now [] (.getTime (java.util.Date.)))



;;; "persistence"

(def default-db-file "db.dat")
(defn read-db
  ([] (read-db default-db-file))
  ([file]
     (try (read-string (slurp file))
          (catch java.io.FileNotFoundException _
            (println "db file didn't exist. that's okay." ()))
          (catch Exception e
            (println "got exception while reading db! crazy!" e) ()))))

(defonce db (atom (read-db)))

(defn write-db
  ([] (write-db default-db-file))
  ([file] (spit file (pr-str @db))))



;;; requests

(defn post [txt]
  (swap! db conj [(now) txt])
  (write-db)
  (response "posted"))

(defn get-posts [time]
  (let [later-than (try (BigInteger. time) (catch Exception _ nil))
        posts (if (nil? later-than)
                @db
                (into [] (take-while (fn [[date _]] (> date later-than)) @db)))]
    (response (str posts))))

(defn remove-post [id]
  (let [id (try (BigInteger. id) (catch Exception _ nil))]
    (if (nil? id)
      (response (str false))
      (do (swap! db #(filter (fn [[time _]] (not= time id)) %))
          (response (str true))))))
