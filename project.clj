(defproject d3-compat-tree "0.0.4"
  :description "A d3 tree layout compatible data structure for Clojure using zippers"
  :url "https://github.com/borh/d3-compat-tree.git"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [fast-zip "0.6.1" :exclusions [com.cemerick/austin]]
                 [prismatic/plumbing "0.4.1"]
                 [prismatic/schema "0.4.0"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.7.0"]]}}
  :min-lein-version "2.0.0")
