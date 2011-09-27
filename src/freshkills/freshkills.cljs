(ns freshkills.main
  (:require [goog.dom :as dom]
            [goog.string :as string]
            [goog.net.XhrIo :as Xhr]
            [goog.events.EventType :as EventType]
            [goog.events :as events]
            [goog.date :as date]
            [goog.Timer :as Timer]
            [cljs.reader :as reader]))

;;; utils

;; silly output junk. make it better plz.

(defn html [s] (dom/htmlToDocumentFragment s))

(defn js-alert [msg]
  (js* "alert(~{msg})"))

(defn out-prim
  ([s elt] (dom/append (dom/getElement elt) s))
  ([s] (out-prim s "killed")))

(defn out-insert [dom]
  (dom/insertChildAt (dom/getElement "killed") dom 0))

(defn ms->date [ms]
  (doto (goog.date.DateTime.) (. (setTime ms))))



;;; formatting

(defn linkify [s]
  (. s (replace (js* "/([a-z]+:\\/\\/\\S+)/ig") "<a href=\"$1\">$1</a>")))

(defn format-date [ms]
  (. (ms->date ms) (toIsoString true)))

(defn format-post [s]
  (linkify (goog.string.htmlEscape s true)))



;;; real stuff

(defn insert-post-html [[date val]]
  (let [hidden (js* "{'style' : 'visibility: hidden'} ")
        visible (js* "{'style' : 'visibility: visible'} ")
        button (dom/createDom "button" hidden "x")
        div (dom/createDom
             "div" nil button
             (html (str "<small><small>" (format-date date) "&gt;</small></small> "
                        (format-post val))))
        k (fn [e]
            (if (-> (.target e) (. (getResponseText)) reader/read-string)
              (dom/removeNode div)
              (js-alert "failed to remove!")))
        rm-on-click (fn [] (Xhr/send (str "/rm?id=" date) k))]
    (events/listen div goog.events.EventType.MOUSEOVER (fn [] (dom/setProperties button visible)))
    (events/listen div goog.events.EventType.MOUSEOUT (fn [] (dom/setProperties button hidden)))
    (events/listen button goog.events.EventType.CLICK rm-on-click)
    (out-insert div)))

;; TODO redo nice date dedup
(defn insert-posts-html [posts]
  (doseq [p posts] (insert-post-html p)))

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
              (when-not (empty? new-posts)
                (let [[[latest _post]] new-posts]
                  (reset! latest-post-date latest)
                  (insert-posts-html (reverse new-posts))))))]
    (Xhr/send url k)))

(defn ^:export post []
  (let [txt (.value (dom/getElement "txt"))
        clear&reload (fn [_e]
                       (load-posts)
                       (set! (.value (dom/getElement "txt")) ""))]
    (if-not (string/isEmpty txt)
      (Xhr/send "/post" clear&reload "POST" (map->uri-opts {:txt txt}))
      (js-alert "only whitespace!"))
    ;; stop form submission
    false))

(defn ^:export start-auto-loader
  ([] (start-auto-loader 5000))
  ([interval]
     (let [timer (goog.Timer. interval)]
       (load-posts)
       (events/listen timer goog.Timer/TICK load-posts)
       (. timer (start)))))
