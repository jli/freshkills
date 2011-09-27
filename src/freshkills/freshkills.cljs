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

(defn js-alert [& args]
  (let [msg (apply str args)]
    (js* "alert(~{msg})")))

(defn out-prim
  ([s elt] (dom/append (dom/getElement elt) s))
  ([s] (out-prim s "killed")))

(defn out-insert [dom]
  (dom/insertChildAt (dom/getElement "killed") dom 0))

(defn ms->date [ms]
  (doto (goog.date.DateTime.) (. (setTime ms))))

(defn event->clj [e]
  (-> (.target e)
      (. (getResponseText))
      reader/read-string))



;;; formatting

(defn linkify [s]
  (. s (replace (js* "/([a-z]+:\\/\\/\\S+)/ig") "<a href=\"$1\">$1</a>")))

(defn format-date [ms]
  (. (ms->date ms) (toIsoString true)))

(defn format-post [s]
  (html (linkify (string/htmlEscape s true))))



;;; real stuff

(defn map->uri-opts [m]
  (let [pairs (map (fn [[k v]] (str (name k) "=" (js/encodeURIComponent v))) m)]
    (apply str (interpose "&" pairs))))


;; FIXME only delete for untagged? delete when text blank?
;; FIXME make this less horrifying!
(defn insert-post-html [[date val]]
  (let [hidden (js* "{'style' : 'visibility: hidden'} ")
        visible (js* "{'style' : 'visibility: visible'} ")
        delete-button (dom/createDom "button" nil "x")
        edit-button (dom/createDom "button" nil "edit")
        buttons (dom/createDom "span" hidden delete-button edit-button)
        val-dom (atom (dom/createDom "span" nil (format-post val)))
        set-val! (fn [new]
                   (let [old @val-dom]
                     (reset! val-dom new)
                     (dom/replaceNode new old)))
        div (dom/createDom
             "div" nil buttons
             (html (str "<small><small>" (format-date date) "&gt;</small></small> "))
             @val-dom)
        rm-k (fn [e]
               (if (true? (event->clj e))
                 (dom/removeNode div)
                 (js-alert "failed to remove!")))
        rm (fn [] (Xhr/send (str "/rm?id=" date) rm-k))
        submit-k (fn [e new-val]
                   (if (true? (event->clj e))
                     (set-val! (dom/createDom "span" nil (format-post new-val)))
                     (js-alert "failed to edit!")))
        submit-edit (fn [input]
                      (let [new (.value input)]
                        (Xhr/send "/edit" (fn [e] (submit-k e new)) "POST"
                                  (map->uri-opts {:id date :txt new}))
                        ;; stop form submit
                        false))
        ;; FIXME form appears on separate line
        edit (fn []
               (let [val (dom/getTextContent @val-dom)
                     input (dom/createDom "input" (js* "{'type':'textbox', 'value':~{val}}"))
                     editor (dom/createDom "form" nil input)]
                 (set-val! editor)
                 (. input (focus))
                 (set! (.onsubmit editor) (fn [] (submit-edit input)))))]
    (events/listen div goog.events.EventType.MOUSEOVER (fn [] (dom/setProperties buttons visible)))
    (events/listen div goog.events.EventType.MOUSEOUT (fn [] (dom/setProperties buttons hidden)))
    (events/listen delete-button goog.events.EventType.CLICK rm)
    (events/listen edit-button goog.events.EventType.CLICK edit)
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