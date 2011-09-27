(ns freshkills.dump
  (:use [ring.util.response :only [response]]))



;;; utils

(defn now [] (.getTime (java.util.Date.)))

(defn safe-bigint [s] (try (BigInteger. s) (catch Exception _ nil)))



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

(defn swap-db! [& rest]
  (apply swap! db rest)
  (write-db))

(defn post [txt]
  (swap-db! conj [(now) txt])
  (response "posted"))

(defn get-posts [time]
  (let [later-than (safe-bigint time)
        posts (if (nil? later-than)
                @db
                (take-while (fn [[date _]] (> date later-than)) @db))]
    ;; FIXME is into necessary?
    (response (str (into [] posts)))))


(defn remove-post [id]
  (let [id (safe-bigint id)]
    (if (nil? id)
      (response (str false))
      (do (swap-db! #(filter (fn [[time _]] (not= time id)) %))
          (response (str true))))))

(defn edit-post [id txt]
  (if-let [id (safe-bigint id)]
    (let [replace
          (fn [acc posts]
            (if-let [[[pid _ :as first] & rest] posts]
              (cond
               (= id pid) (concat (conj acc [id txt]) rest)
               (< id pid) (recur (conj acc first) rest)
               :default (concat acc posts)) ;failed
              acc))                         ;failed
          ]
      (swap-db! #(replace [] %))
      (response (str true)))
    ;; couldn't parse id
    (response (str false))))

