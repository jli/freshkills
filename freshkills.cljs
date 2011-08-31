(ns freshkills.main
  (:require [goog.dom :as dom]
            [goog.net.XhrIo :as Xhr]
            [goog.events :as events]
            [goog.date :as date]
            [cljs.reader :as reader]))

;;; utils

;; silly output junk. make it better plz.

(defn out-prim
  ([s elt] (dom/append (dom/getElement elt) s))
  ([s] (out-prim s "killed")))

(defn out-html [html] (out-prim (dom/htmlToDocumentFragment html)))
(defn out-line [] (out-html "<br>"))
(defn out-insert-html [html]
  (dom/insertChildAt (dom/getElement "killed") (dom/htmlToDocumentFragment html) 0))

;; (defn out [& args]
;;   (out-prim (apply str args))
;;   (out-prim (dom/htmlToDocumentFragment " ")))

(defn date-ms [ms]
  (doto (goog.date.DateTime.) (. (setTime ms))))



;;; formatting

;;; madness #"(?i)(https?://[\\$-_@\\.&\\+!\\*\"'\\(\\),%:;#a-zA-Z0-9/]+)"
(def link-rex #"(?i)([a-z]+://\S+)")

(defn linkify [s]
  ;;   (-> (.matcher link-rex s)
  ;; (.replaceAll "<a href=\"$1\">$1</a>")
  s
  )

;; (def date-fmt (java.text.SimpleDateFormat. "yyyy-MM-dd"))
;; (def time-fmt (java.text.SimpleDateFormat. "HH:mm:ss"))
;; (defn time-str [d] (.format time-fmt d))
;; (defn date-str [d] (format "<b>%s</b> %s"
;;                            (.format date-fmt d)
;;                            (time-str d)))

;; (defn date-day [date]
;;   (let [cal (doto (java.util.Calendar/getInstance)
;;               (.setTime date))
;;         field (fn [field] (.get cal field))]
;;     (.get cal java.util.Calendar/DAY_OF_YEAR)))

;; ;;; only shows day once in a sequence
;; (defn db-format-date [db]
;;   (let [prev-day (atom false)]
;;     (map (fn [[date val]]
;;            (let [day (date-day date)]
;;              (if (= day @prev-day)
;;                [(time-str date) val]
;;                (do (swap! prev-day (constantly day))
;;                    [(date-str date) val]))))
;;          db)))

(defn format-date [ms]
  (. (date-ms ms) (toIsoString true)))

(defn format-post [s]
  ;;(linkify (StringEscapeUtils/escapeHtml s))
  (linkify s)
  )

(defn posts->html [posts]
  (let [divs (map (fn [[date val]]
                    (str "<div>" (format-date date)
                         " - <pre>" (format-post val) "</pre></div>"))
                  posts)]
    (apply str divs)))


;;; real stuff

;; Date of the latest post on the page.
;; Used by load-posts to request only latest posts.
(def latest-post-date (atom nil))

(defn ^:export load-posts []
  (let [params (if-let [latest @latest-post-date]
                (str "?laterthan=" latest)
                "")
        k (fn [e]
            (let [new-posts (-> (.target e)
                                (. (getResponseText)) ;;bleh
                                (reader/read-string))
                  html (posts->html new-posts)
                  [[latest _post]] new-posts
                  ]
              (reset! latest-post-date latest)
              (out-insert-html html)))]
    (Xhr/send (str "/get" params) k)))

(defn ^:export post []
  (let [clear&reload (fn [_e]
                       ;; make this less horrifying
                       (js* "document.getElementById('txt').value=\"\"")
                       (load-posts))]
    (Xhr/send "/post" clear&reload "POST" (.value (dom/getElement "txt")))
    ;; prevent form submission by returning false
    false))
