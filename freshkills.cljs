(ns freshkills.main
  (:require [goog.dom :as dom]
            [cljs.reader :as reader]))

(defn out-prim [s]
  (dom/append (dom/getElement "out") s))

(defn out-line [] (out-prim (dom/htmlToDocumentFragment "<br>")))

(defn out [& args]
  (out-prim (apply str args))
  (out-prim (dom/htmlToDocumentFragment " ")))

(defn ^:export post [data]
  (out "fixme" data))
