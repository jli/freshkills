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

;; no need to escape html when not passing string into
;; dom/htmlToDocumentFragment
(defn format-post [s]
  (linkify s))



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
        val-span (atom (dom/createDom "span" nil (format-post val)))
        div (dom/createDom
             "div" nil buttons
             (html (str "<small><small>" (format-date date) "&gt;</small></small> "))
             @val-span)
        rm-k (fn [e]
               (if (true? (event->clj e))
                 (dom/removeNode div)
                 (js-alert "failed to remove!")))
        rm (fn [] (Xhr/send (str "/rm?id=" date) rm-k))
        ;; FIXME rewire edit button properly
        submit-k (fn [e editor new-val]
                   (if (true? (event->clj e))
                     (let [v2 (dom/createDom "span" nil (format-post new-val))]
                       (do (dom/replaceNode v2 editor)
                           (reset! val-span v2)))
                     (js-alert "failed to edit!")))
        submit-edit (fn [editor input]
                      (let [new (.value input)]
                        (Xhr/send "/edit" (fn [e] (submit-k e editor new)) "POST"
                                  (map->uri-opts {:id date :txt new}))
                        ;; stop form submit
                        false))
        edit (fn []
               ;; FIXME form appears on separate line
               (let [input (dom/createDom "input" (js* "{'type':'textbox', 'value':~{val}}"))
                     editor (dom/createDom "form" nil input)]
                 (dom/replaceNode editor @val-span)
                 (. input (focus))
                 (set! (.onsubmit editor) (fn [] (submit-edit editor input)))))]
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
