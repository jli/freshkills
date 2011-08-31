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

(defn out-insert-html [html]
  (dom/insertChildAt (dom/getElement "killed") (dom/htmlToDocumentFragment html) 0))

(defn ms->date [ms]
  (doto (goog.date.DateTime.) (. (setTime ms))))



;;; formatting

;;; madness #"(?i)(https?://[\\$-_@\\.&\\+!\\*\"'\\(\\),%:;#a-zA-Z0-9/]+)"
(def link-rex #"(?i)([a-z]+://\S+)")

(defn linkify [s]
  (. s (replace link-rex "<a href=\"$1\">$1</a>")))

(defn format-date [ms]
  (. (ms->date ms) (toIsoString true)))

;; TODO proper html escaping.
(defn stupid-escape [s]
  (-> s
      (. (replace (js* "/</g") "&lt;"))
      (. (replace (js* "/>/g") "&gt;"))))
(defn format-post [s]
  (linkify (stupid-escape s)))

(defn posts->html [posts]
  (let [divs (map (fn [[date val]]
                    (str "<div><small><small>" (format-date date)
                         "&gt;</small></small> " (format-post val) "</div>"))
                  posts)]
    (apply str divs)))


;;; real stuff

;; Date of the latest post on the page.
;; Used by load-posts to request only latest posts.
(def latest-post-date (atom nil))

(defn ^:export load-posts []
  (let [url (if-let [latest @latest-post-date]
                (str "/get?laterthan=" latest)
                "/get")
        k (fn [e]
            (let [new-posts (-> (.target e)
                                (. (getResponseText))
                                (reader/read-string))
                  html (posts->html new-posts)
                  [[latest _post]] new-posts]
              (reset! latest-post-date latest)
              (out-insert-html html)))]
    (Xhr/send url k)))

(defn ^:export post []
  (let [clear&reload (fn [_e]
                       ;; make this less horrifying
                       (js* "document.getElementById('txt').value=\"\"")
                       (load-posts))]
    (Xhr/send "/post" clear&reload "POST" (.value (dom/getElement "txt")))
    ;; prevent form submission by returning false
    false))
