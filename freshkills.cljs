(ns freshkills.main
  (:require [goog.dom :as dom]
            [goog.net.XhrIo :as Xhr]
            [goog.events :as events]
            [cljs.reader :as reader]))

;;; silly output junk. make it better plz.

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

;;; formatting

;;; madness #"(?i)(https?://[\\$-_@\\.&\\+!\\*\"'\\(\\),%:;#a-zA-Z0-9/]+)"
(def link-rex #"(?i)([a-z]+://\S+)")

(defn linkify [s]
  ;;   (-> (.matcher link-rex s)
  ;; (.replaceAll "<a href=\"$1\">$1</a>")
  s
  )

(defn format-dump [s]
  ;;(linkify (StringEscapeUtils/escapeHtml s))
  (linkify s)
  )

(defn posts->html [posts]
  (let [divs (map (fn [[date val]]
                     (str "<div>" date " - <pre>" (format-dump val) "</pre></div>"))
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
