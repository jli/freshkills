(ns freshkills.main
  (:require [goog.dom :as dom]
            [goog.string :as string]
            [goog.array :as array]
            [goog.net.XhrIo :as Xhr]
            [goog.events.EventType :as EventType]
            [goog.events :as events]
            [goog.date :as date]
            [goog.Timer :as Timer]
            [cljs.reader :as reader]))

;;; utils

(def hidden (.strobj {"style" "visibility: hidden"}))
(def visible (.strobj {"style" "visibility: visible"}))

(def html dom/htmlToDocumentFragment)
(def node dom/createDom)

(defn js-alert [& args]
  (let [msg (apply str args)]
    (js* "alert(~{msg})")))

(defn ms->date [ms]
  (doto (goog.date.DateTime.) (. (setTime ms))))

(defn uri-opts [m]
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



;;; tags

;; tags start with "#", are at least 1 char, and are surrounded by
;; whitespace (or at the beginning)
(defn parse-tags [str]
  (let [tags (into #{} (.match str (js* "/(^|\\s)#[^\\s]+/g")))]
    (if (empty? tags)
      #{"nocat"}
      tags)))

(defn tag->section-id [tag]
  (str "tagid-" (.replace tag (js* "/[^a-zA-Z0-9]/g") "_")))

;; classes can't begin with digit
(defn date->post-class [date] (str "c" date))

;; want to sort based on full tag name. use the h3 in each section!
(defn insert-section [node tag]
  (let [killed (dom/getElement "killed")
        childs (array/toArray (dom/getChildren killed))
        [sib] (filter #(> (dom/getTextContent (.firstChild %)) tag) childs)]
    (if sib
      (dom/insertSiblingBefore node sib)
      (dom/insertChildAt killed node 0))))

(defn tag-insert [tag node]
  (let [section  (dom/getElement (tag->section-id tag))
        sibs (array/toArray (dom/getChildren section))
        [sib] (filter #(> (.class %) (.class node)) sibs)]
    (if sib
      (dom/insertSiblingBefore node sib)
      (dom/insertChildAt section node 1)))) ;; 0th position is header

(defn ensure-tag-section [tag]
  (let [tag-id (tag->section-id tag)]
    (or (dom/getElement tag-id)
        (let [section (node "div" (.strobj {"id" tag-id})
                            (node "h3" nil tag))]
          (insert-section section tag)
          section))))

(defn maybe-remove-section [section]
  ;; the 1 child is the header with tag name
  (when (= 1 (count (array/toArray (dom/getChildren section))))
    (dom/removeNode section)))

(defn maybe-remove-tag-section [tag]
  (let [tag-id (tag->section-id tag)]
    (if-let [section (dom/getElement tag-id)]
      (maybe-remove-section section))))

(defn cleanup-sections []
  (doseq [c (array/toArray (dom/getChildren (dom/getElement "killed")))]
    (maybe-remove-section c)))



;;; real stuff

(defn get-all-posts [date]
  (array/toArray (dom/getElementsByTagNameAndClass "div" (date->post-class date))))

;; remove post in all tag sections
(defn remove-post [date]
  (let [ns (get-all-posts date)]
    (doseq [n ns] (dom/removeNode n))
    ;; removal may have left multiple sections empty. inspects all
    ;; tags. slow blah blah.
    (cleanup-sections)))

;; FIXME probably leaks, right?
(defn rm-handler [date]
  (Xhr/send (str "/rm?id=" date)
            (fn [e] (if (true? (event->clj e))
                      (remove-post date)
                      (js-alert "failed to remove!")))))

;; FIXME form appears on separate line
(defn edit-handler [date val-node]
  (let [k (fn [e new-val]
            (if-not (true? (event->clj e))
              (js-alert "failed to edit!")
              (do
                (remove-post date)
                (insert-posts [[date new-val]]))))
        submit (fn [input]
                 (let [new (.value input)]
                   (Xhr/send "/edit" #(k % new) "POST" (uri-opts {:id date :txt new}))
                   ;; stop form submit
                   false))
        val (dom/getTextContent val-node)
        input (node "input" (.strobj {"type" "textbox" "value" val}))
        editor (node "form" nil input)]
    (set! (.onsubmit editor) #(submit input))
    (dom/replaceNode editor val-node)
    (. input (focus))))

;; only delete for untagged?
(defn insert-tagged-post [tag [date val]]
  (let [tag-section (ensure-tag-section tag)
        rm-button (node "button" nil "x")
        edit-button (node "button" nil "edit")
        buttons (node "span" hidden rm-button edit-button)
        val-node (node "span" nil (render-post val))
        div (node "div" (.strobj {"class" (date->post-class date)})
                  buttons (render-date date) val-node)]
    (events/listen div events/EventType.MOUSEOVER     #(dom/setProperties buttons visible))
    (events/listen div events/EventType.MOUSEOUT      #(dom/setProperties buttons hidden))
    (events/listen rm-button events/EventType.CLICK   #(rm-handler date))
    (events/listen edit-button events/EventType.CLICK #(edit-handler date val-node))
    (tag-insert tag div)))

(defn insert-posts [posts]
  (doseq [[_time val :as post] posts
          tag (parse-tags val)]
    (insert-tagged-post tag post)))

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
                  (insert-posts (reverse new-posts))))))]
    (Xhr/send url k)))

(defn ^:export post []
  (let [txt (.value (dom/getElement "txt"))
        clear&reload (fn [_e]
                       (load-posts)
                       (set! (.value (dom/getElement "txt")) ""))]
    (if-not (string/isEmpty txt)
      (Xhr/send "/post" clear&reload "POST" (uri-opts {:txt txt}))
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
