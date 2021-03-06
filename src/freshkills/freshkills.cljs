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
  (html (str "<small><small>" (format-date ms) "</small></small> ")))

(defn render-post [tag s]
  ;; not great. usually right. extra whitespace matching to try to
  ;; avoid destroying links. doesn't work with repeated tags, not sure
  ;; how to use non-capturing
  (let [spanify-tag #(.replace % (js/RegExp (str "(^|\\s)(" tag ")(\\s)") "g")
                               "$1<span class='tag'>$2</span>$3")]
    (-> s string/htmlEscape spanify-tag linkify html)))



;;; tags

(defn valid-id? [s]
  (.test (js* "/^[-a-zA-Z0-9_:.]+$/") s))

(defn valid-tag? [s]
  (and (.test #"^#" s)
       (valid-id? (.replace s #"^#" ""))))

;; tags start with "#", are at least 1 char, and only use valid html
;; attribute names. only try to parse tags from beginning of post.
(defn parse-tags [str]
  (let [words (.split str (js* "/\\s/"))
        tags (take-while valid-tag? words)]
    tags))

;; no need to safeguard - parse-tags only accepts valid chars
(defn tag->section-id [tag] (str "tagid-" tag))

;; classes can't begin with digit
(defn date->post-class [date] (str "c" date))

;; want to sort based on full tag name. use the h2 in each section!
(defn insert-section [node tag]
  (let [killed (dom/getElement "killed")
        childs (array/toArray (dom/getChildren killed))
        ;; nocat always top
        greater (fn [t1 t2]
                  (cond
                   (= "nocat" t1) false
                   (= "nocat" t2) true
                   :default (> t1 t2)))
        [sib] (filter #(greater (dom/getTextContent (.firstChild %)) tag) childs)]
    (if sib
      (dom/insertSiblingBefore node sib)
      (dom/insertChildAt killed node (count childs)))))

(defn tag-insert [tag node]
  (let [section (dom/getElement (tag->section-id tag))
        ;; 1st child is header
        sibs (rest (array/toArray (dom/getChildren section)))
        [sib] (filter #(< (.className %) (.className node)) sibs)]
    (if sib
      (dom/insertSiblingBefore node sib)
      (dom/insertChildAt section node (inc (count sibs))))))

(defn ensure-tag-section [tag]
  (let [tag-id (tag->section-id tag)]
    (or (dom/getElement tag-id)
        (let [section (node "div" (.strobj {"id" tag-id})
                            (node "h2" nil tag))]
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

(defn edit-handler [date val-node]
  (let [k (fn [e new-val]
            (if-not (true? (event->clj e))
              (js-alert "failed to edit!")
              (do (remove-post date)
                  (insert-posts [[date new-val]]))))
        submit (fn [event input]
                 (when (= 13 (.charCode event)) ; enter
                   (let [new (.value input)]
                     (Xhr/send "/edit" #(k % new) "POST" (uri-opts {:id date :txt new})))))
        unsubmit (fn [_event input] (dom/replaceNode val-node input))
        input (node "input" (.strobj {"type" "text"
                                      "value" (.rawtext val-node)
                                      "style" "width: 50%"}))]
    (events/listen input events/EventType.KEYPRESS #(submit % input))
    (events/listen input events/EventType.BLUR #(unsubmit % input))
    (dom/replaceNode input val-node)
    (. input (focus))))

;; only delete for untagged?
;; todo prevent rm/edit clicks from changing URL?
(defn insert-tagged-post [tag [date val]]
  (let [tag-section (ensure-tag-section tag)
        edit-class (.strobj {"class" "edit" "href" "#"})
        rm (node "a" edit-class "rm")
        edit (node "a" edit-class "ed")
        val-node (node "span" (.strobj {"class" "killtxt"}) (render-post tag val))
        div (node "div" (.strobj {"class" (date->post-class date)})
                  rm " " edit  " " (render-date date) val-node)]
    ;; used for editing original text
    (set! (.rawtext val-node) val)
    (events/listen rm events/EventType.CLICK #(rm-handler date))
    (events/listen edit events/EventType.CLICK #(edit-handler date val-node))
    (tag-insert tag div)))

(defn insert-posts [posts]
  (doseq [[_time val :as post] posts
          tag (let [ts (parse-tags val)]
                (if (empty? ts) #{"nocat"} (set ts)))]
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
