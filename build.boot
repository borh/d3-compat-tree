(require '[clojure.java.shell :as sh])

(defn next-version [version]
  (when version
    (let [[a b] (next (re-matches #"(.*?)([\d]+)" version))]
      (when (and a b)
        (str a (inc (Long/parseLong b)))))))

(defn deduce-version-from-git
  "Avoid another decade of pointless, unnecessary and error-prone
  fiddling with version labels in source code."
  []
  (let [[version commits hash dirty?]
        (next (re-matches #"(.*?)-(.*?)-(.*?)(-dirty)?\n"
                          (:out (sh/sh "git" "describe" "--dirty" "--long" "--tags" "--match" "[0-9].*"))))]
    (try
      (cond
        dirty? (str (next-version version) "-" hash "-dirty")
        (pos? (Long/parseLong commits)) (str (next-version version) "-" hash)
        :otherwise version)
      (catch Exception e (println "Not a git repository or empty repository (did you forget to tag?). Please git init in this directory/make a commit and tag a version.")))))

(def project "d3-compat-tree")
(def version (deduce-version-from-git))

(set-env! :resource-paths #{"src"}
          :source-paths   #{"test"}
          :dependencies   '[[org.clojure/clojure "1.9.0"]

                            [adzerk/boot-test "RELEASE" :scope "test"]
                            [org.clojure/test.check "0.10.0-alpha2" :scope "test"]
                            [adzerk/bootlaces "0.1.13" :scope "test"]

                            [fast-zip "0.6.1" :exclusions [com.cemerick/austin]]
                            [prismatic/schema "1.1.7"]
                            [prismatic/plumbing "0.5.5"]])

(task-options!
 pom {:project     (symbol project)
      :version     version
      :description "A d3 tree layout compatible data structure for Clojure using zippers."
      :url         "https://github.com/borh/d3-compat-tree"
      :scm         {:url "https://github.com/borh/d3-compat-tree"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}}
 jar {:main 'corpus-utils.text :file (str project "-" version ".jar")})

(require '[adzerk.bootlaces :refer :all])

(bootlaces! version)

(deftask build
  "Build and install the project locally."
  []
  (comp (pom) (jar) (target) (install)))

(deftask dev
  []
  (comp (watch) (build) (repl :init-ns 'corpus-utils.text :server true)))

(require '[adzerk.boot-test :refer [test]])
