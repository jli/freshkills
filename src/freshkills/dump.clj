(ns freshkills.dump
  (:use [ring.util.response :as response]
        [ring.middleware.file :only [wrap-file]]
        [match.core :only [match]]))

(defonce db (atom {}))

(def date-fmt (java.text.SimpleDateFormat.))
(defn date-str [d] (.format date-fmt d))

(defn main-page []
  "<html><head><title>dump</title></head><body>
<h1>PUSH IT</h1>
<form action=\"/txt?in\" id=\"in\" method=\"post\">
<input type=\"textarea\" name=\"txt\"></input><br>
<input type=\"submit\" id=\"submit\" value=\"YEAH\"></input>
</form>
</body></html>")

(defn input-page [req]
  (let [r (slurp (:body req))]
    (swap! db assoc (java.util.Date.) r)
    (format
     "<html><head><title>dumped</title></head><body>
<h1>PUSHED IT</h1>
<big><big><big><strong>THX</strong></big></big></big>
%s
<br>body: %s
<br>db: %s
</body></html>" req r @db)))

(defn output-page []
  (let [db-fmt (map (fn [[k v]]
                      (format "<div><b>%s</b> - %s</div>" (date-str k) v))
                    @db)]
    (format "<html><head><title>out</title></head><body>
<h1>stuff</h1>
<div>%s</div>
</body></html>" (apply str db-fmt))))

(defn handler [req]
  ;; guh. :query-string always in req, but can be nil.
  (let [qstr (if-let [q (:query-string req "")] q "")]
    (condp #(.startsWith %2 %1) qstr
      "in" (input-page req)
      "out" (output-page)
      (main-page))))
