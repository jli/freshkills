(ns freshkills.main
  (:require [goog.dom :as dom]
            [goog.net.XhrIo :as Xhr]
            [goog.events :as events]
            [goog.date :as date]
            [goog.Timer :as Timer]
            [cljs.reader :as reader]))

(def auto-load-interval 5000)

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

(defn linkify [s]
  (. s (replace (js* "/([a-z]+:\\/\\/\\S+)/ig") "<a href=\"$1\">$1</a>")))

(defn format-date [ms]
  (. (ms->date ms) (toIsoString true)))

(defn format-post [s]
  (linkify (goog.string.htmlEscape s true)))

(defn post->html [[date val]]
  (let [delete-button nil] ;; implement
    (str "<div id=\"" date "\"><small><small>" (format-date date)
         "&gt;</small></small> " (format-post val) "</div>")))

;; TODO redo nice date dedup
(defn posts->html [posts]
  (apply str (map post->html posts)))


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
                                reader/read-string)]
              (when (not (empty? new-posts))
                (let [html (posts->html new-posts)
                      [[latest _post]] new-posts]
                  (reset! latest-post-date latest)
                  (out-insert-html html)))))]
    (Xhr/send url k)))

(defn ^:export post []
  (let [clear&reload (fn [_e]
                       (load-posts)
                       ;; make this less horrifying
                       (js* "document.getElementById('txt').value=\"\""))]
    (Xhr/send "/post" clear&reload "POST" (.value (dom/getElement "txt")))
    ;; prevent form submission by returning false
    false))

(defn ^:export start-auto-loader []
  (let [timer (goog.Timer. auto-load-interval)]
    (load-posts)
    (events/listen timer goog.Timer/TICK load-posts)
    (. timer (start))))
