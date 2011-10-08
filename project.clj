(defproject freshkills "0.1"
  :description "dumping grounds"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [ring/ring-core "0.3.11"]
                 [ring/ring-devel "0.3.11"]
                 [ring/ring-jetty-adapter "0.3.11"]
                 [amalloy/ring-gzip-middleware "0.1.0"]
                 [ring-basic-authentication "0.0.1"]
                 [compojure "0.6.5"]]
  :main freshkills.core)
