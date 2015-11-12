(defproject d3-compat-tree "0.0.9"
  :description "A d3 tree layout compatible data structure for Clojure using zippers"
  :url "https://github.com/borh/d3-compat-tree.git"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [fast-zip "0.6.1" :exclusions [com.cemerick/austin]]
                 [prismatic/plumbing "0.5.2"]
                 [prismatic/schema "1.0.3"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.8.2"]]}}
  :min-lein-version "2.0.0")
