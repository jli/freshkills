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

(def html dom/htmlToDocumentFragment)
(def make-dom dom/createDom)

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

(defn map->uri-opts [m]
  (let [pairs (map (fn [[k v]] (str (name k) "=" (js/encodeURIComponent v))) m)]
    (apply str (interpose "&" pairs))))

(defn event->clj [e]
   (-> (.target e)
       (. (getResponseText))
       reader/read-string))



;;; formatting

(defn linkify [s]
  (.replace s (js* "/([a-z]+:\\/\\/\\S+)/ig") "<a href=\"$1\">$1</a>"))

(defn format-date [ms]
  (.toIsoString (ms->date ms) true))

(defn render-date [ms]
  (html (str "<small><small>" (format-date ms) "&gt;</small></small> ")))

(defn render-post [s]
  (html (linkify (string/htmlEscape s true))))



;;; real stuff

(def hidden (.strobj {"style" "visibility: hidden"}))
(def visible (.strobj {"style" "visibility: visible"}))

(defn replace-dom! [dom-atom new-dom]
  (let [old-dom @dom-atom]
    (reset! dom-atom new-dom)
    (dom/replaceNode new-dom old-dom)))

;; FIXME probably leaks, right?
(defn rm-handler [date post-div]
  (Xhr/send (str "/rm?id=" date)
            (fn [e] (if (true? (event->clj e))
                      (dom/removeNode post-div)
                      (js-alert "failed to remove!")))))

;; FIXME form appears on separate line
(defn edit-handler [date val-dom]
  (let [;; when xhr returns, set new post value
        submit-k (fn [e new-val]
                   (if (true? (event->clj e))
                     (replace-dom! val-dom (make-dom "span" nil (render-post new-val)))
                     (js-alert "failed to edit!")))
        ;; wire up form to send xhr
        submit-edit (fn [input]
                      (let [new (.value input)]
                        (Xhr/send "/edit" (fn [e] (submit-k e new)) "POST"
                                  (map->uri-opts {:id date :txt new}))
                        ;; stop form submit
                        false))]
    (let [val (dom/getTextContent @val-dom)
          input (make-dom "input" (.strobj {"type" "textbox" "value" val}))
          editor (make-dom "form" nil input)]
      (replace-dom! val-dom editor)
      (. input (focus))
      (set! (.onsubmit editor) #(submit-edit input)))))


;; FIXME only delete for untagged? delete when text blank?
(defn insert-post-html [[date val]]
  (let [;; dom stuff
        delete-button (make-dom "button" nil "x")
        edit-button (make-dom "button" nil "edit")
        buttons (make-dom "span" hidden delete-button edit-button)
        val-dom (atom (make-dom "span" nil (render-post val)))
        div (make-dom "div" nil buttons (render-date date) @val-dom)]
    (events/listen div events/EventType.MOUSEOVER       #(dom/setProperties buttons visible))
    (events/listen div events/EventType.MOUSEOUT        #(dom/setProperties buttons hidden))
    (events/listen delete-button events/EventType.CLICK #(rm-handler date div))
    (events/listen edit-button events/EventType.CLICK   #(edit-handler date val-dom))
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
            (let [new-posts (event->clj e)]
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
